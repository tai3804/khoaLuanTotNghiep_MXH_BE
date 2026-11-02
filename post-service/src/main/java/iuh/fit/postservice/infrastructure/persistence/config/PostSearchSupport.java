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

    @Override
    public void run(ApplicationArguments args) {
        initUnaccent();
        initVector();
    }

    private void initUnaccent() {
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

    private void initVector() {
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
        return unaccentAvailable;
    }

    public boolean isVectorAvailable() {
        return vectorAvailable;
    }

    /** Từ khoá → mẫu LIKE (ký tự thoát là '!'): chữ thường, bỏ dấu nếu DB hỗ trợ, thoát % _ ! do người dùng gõ. */
    public String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > 100) k = k.substring(0, 100);
        k = k.toLowerCase(Locale.ROOT);
        if (unaccentAvailable) {
            k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd');
        }
        k = k.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
