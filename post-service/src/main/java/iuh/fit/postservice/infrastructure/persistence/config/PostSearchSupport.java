package iuh.fit.postservice.infrastructure.persistence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Hỗ trợ tìm kiếm bài viết:
 * 1. unaccent: Tìm bài viết không phân biệt dấu ("ca phe" -> "Cà phê").
 * 2. pgvector: Bật extension vector, khởi tạo bảng post_embeddings (768 chiều) và chỉ mục HNSW cho tìm kiếm ngữ nghĩa (Semantic Search).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostSearchSupport implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean unaccentAvailable = false;
    private volatile boolean vectorAvailable = false;
    private volatile boolean fullTextAvailable = false;
    private volatile long lastFullTextAttemptMs = 0;

    @Override
    public void run(ApplicationArguments args) {
        initUnaccent();
        initVector();
        initFullText();
    }

    /**
     * Chỉ mục đảo (inverted index) cho truy xuất từ khoá, thay cho LIKE '%x%' quét toàn bảng:
     * cột posts.search_tsv (tsvector, cấu hình 'simple' vì tiếng Việt không có stemming) + chỉ mục GIN, được trigger tự cập nhật.
     * Hàm kltn_fold() phải cho kết quả giống SearchTextAnalyzer.fold() phía Java: chữ thường, bỏ dấu, đ -> d.
     */
    private synchronized void initFullText() {
        lastFullTextAttemptMs = System.currentTimeMillis();
        try {
            if (!unaccentAvailable) {
                fullTextAvailable = false;
                log.warn("Full-text search index disabled: unaccent extension is not available.");
                return;
            }
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION kltn_fold(t text) RETURNS text AS $$
                    SELECT translate(public.unaccent('public.unaccent', lower(coalesce(t, ''))), 'đ', 'd')
                $$ LANGUAGE sql IMMUTABLE PARALLEL SAFE
            """);
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN IF NOT EXISTS search_tsv tsvector");
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION posts_search_tsv_update() RETURNS trigger AS $$
                BEGIN
                    NEW.search_tsv := to_tsvector('simple', kltn_fold(NEW.content));
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql
            """);
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_posts_search_tsv ON posts");
            jdbcTemplate.execute("""
                CREATE TRIGGER trg_posts_search_tsv BEFORE INSERT OR UPDATE OF content ON posts
                FOR EACH ROW EXECUTE FUNCTION posts_search_tsv_update()
            """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_posts_search_tsv ON posts USING gin (search_tsv)");
            int backfilled = jdbcTemplate.update(
                    "UPDATE posts SET search_tsv = to_tsvector('simple', kltn_fold(content)) WHERE search_tsv IS NULL");
            fullTextAvailable = true;
            log.info("Full-text search index (GIN on posts.search_tsv) ready, backfilled {} posts", backfilled);
        } catch (Exception e) {
            fullTextAvailable = false;
            log.warn("Could not initialise the full-text search index, keyword retrieval falls back to LIKE: {}", e.getMessage());
        }
    }

    public boolean isFullTextAvailable() {
        if (!fullTextAvailable && System.currentTimeMillis() - lastFullTextAttemptMs > 60_000) {
            initUnaccent();
            initFullText();
        }
        return fullTextAvailable;
    }

    private synchronized void initUnaccent() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            Integer found = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'", Integer.class);
            unaccentAvailable = found != null && found > 0;
            log.info("Accent-insensitive post search is {}", unaccentAvailable ? "enabled" : "disabled");
        } catch (Exception e) {
            unaccentAvailable = false;
            log.warn("Could not enable the unaccent extension, post search stays accent-sensitive: {}", e.getMessage());
        }
    }

    private synchronized void initVector() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            Integer found = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
            vectorAvailable = found != null && found > 0;

            if (vectorAvailable) {
                // Tạo bảng lưu trữ vector 768 chiều cho bài viết
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS post_embeddings (
                        post_id UUID PRIMARY KEY,
                        embedding vector(768) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Tạo chỉ mục HNSW tối ưu Cosine Similarity cho truy vấn sub-millisecond
                jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_post_embeddings_hnsw
                    ON post_embeddings USING hnsw (embedding vector_cosine_ops)
                """);

                // Tạo bảng lưu trữ ý định tìm kiếm của người dùng để bẻ lái đề xuất Bảng tin (Search-biased Recommendation)
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS user_search_interests (
                        user_id UUID PRIMARY KEY,
                        query_text VARCHAR(255) NOT NULL,
                        embedding vector(768) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                log.info("pgvector extension and post_embeddings table with HNSW index successfully initialized!");
            } else {
                log.warn("pgvector extension is not available in PostgreSQL.");
            }
        } catch (Exception e) {
            vectorAvailable = false;
            log.warn("Could not enable pgvector extension: {}", e.getMessage());
        }
    }

    public boolean isUnaccentAvailable() {
        if (!unaccentAvailable) {
            initUnaccent();
        }
        return unaccentAvailable;
    }

    public boolean isVectorAvailable() {
        if (!vectorAvailable) {
            initVector();
        }
        return vectorAvailable;
    }

    /** Từ khoá → mẫu LIKE (ký tự thoát là '!'): chữ thường, bỏ dấu nếu DB hỗ trợ, thoát % _ ! do người dùng gõ. */
    public String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > 100) k = k.substring(0, 100);
        k = k.toLowerCase(Locale.ROOT);
        if (isUnaccentAvailable()) {
            k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd');
        }
        k = k.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
