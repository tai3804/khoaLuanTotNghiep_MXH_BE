package iuh.fit.aiservice.application.service.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bảng {@code knowledge_chunks} (pgvector) chứa các đoạn tài liệu và vector của chúng. Dùng {@link JdbcTemplate} vì cột vector không
 * ánh xạ được bằng JPA. Mỗi thao tác chỉ mượn kết nối trong chốc lát (không giữ qua lời gọi Gemini).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class KnowledgeStore {

    public record StoredChunk(String key, String docSlug, String docTitle, String heading, String audience, String content, double similarity) {
    }

    private final JdbcTemplate jdbc;

    private volatile boolean ready;

    /** Tạo bảng và chỉ mục nếu chưa có. Trả về false khi cơ sở dữ liệu không có pgvector. */
    public synchronized boolean ensureSchema() {
        if (ready) return true;
        try {
            Boolean hasVector = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')", Boolean.class);
            if (!Boolean.TRUE.equals(hasVector)) {
                try {
                    jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
                } catch (Exception e) {
                    log.warn("pgvector is not installed and could not be created: {}", e.getMessage());
                    return false;
                }
            }
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS knowledge_chunks (
                        chunk_key      TEXT PRIMARY KEY,
                        doc_slug       TEXT NOT NULL,
                        doc_title      TEXT,
                        heading        TEXT,
                        audience       TEXT NOT NULL DEFAULT 'user',
                        tags           TEXT,
                        ord            INT NOT NULL DEFAULT 0,
                        content        TEXT NOT NULL,
                        content_hash   VARCHAR(64) NOT NULL,
                        embedding      vector(768),
                        updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_doc ON knowledge_chunks (doc_slug)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_hnsw ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)");
            ready = true;
            return true;
        } catch (Exception e) {
            log.warn("Could not prepare knowledge_chunks: {}", e.getMessage());
            return false;
        }
    }

    /** chunk_key -> content_hash của những đoạn đã có vector. */
    public Map<String, String> hashes() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT chunk_key, content_hash FROM knowledge_chunks WHERE embedding IS NOT NULL",
                rs -> {
                    out.put(rs.getString(1), rs.getString(2));
                });
        return out;
    }

    public void upsert(String key, KnowledgeMarkdownParser.Doc doc, int order, String heading, String content, String hash, String vectorLiteral) {
        jdbc.update("""
                INSERT INTO knowledge_chunks (chunk_key, doc_slug, doc_title, heading, audience, tags, ord, content, content_hash, embedding, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as vector), CURRENT_TIMESTAMP)
                ON CONFLICT (chunk_key) DO UPDATE SET doc_slug = EXCLUDED.doc_slug, doc_title = EXCLUDED.doc_title, heading = EXCLUDED.heading,
                    audience = EXCLUDED.audience, tags = EXCLUDED.tags, ord = EXCLUDED.ord, content = EXCLUDED.content,
                    content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding, updated_at = CURRENT_TIMESTAMP
                """, key, doc.slug(), doc.title(), heading, doc.audience(), String.join(",", doc.tags()), order, content, hash, vectorLiteral);
    }

    /** Cập nhật thứ tự/siêu dữ liệu của đoạn không đổi nội dung (không tạo lại vector). */
    public void touch(String key, KnowledgeMarkdownParser.Doc doc, int order) {
        jdbc.update("UPDATE knowledge_chunks SET doc_title = ?, audience = ?, tags = ?, ord = ? WHERE chunk_key = ?",
                doc.title(), doc.audience(), String.join(",", doc.tags()), order, key);
    }

    /** Xóa các đoạn không còn trong tài liệu. Trả về số đoạn đã xóa. */
    public int deleteNotIn(Set<String> keepKeys) {
        List<String> existing = jdbc.queryForList("SELECT chunk_key FROM knowledge_chunks", String.class);
        int removed = 0;
        for (String key : existing) {
            if (!keepKeys.contains(key)) {
                removed += jdbc.update("DELETE FROM knowledge_chunks WHERE chunk_key = ?", key);
            }
        }
        return removed;
    }

    /** Các đoạn gần câu hỏi nhất theo cosine (giảm dần). Việc lọc theo ngưỡng và đối tượng đọc làm ở tầng trên. */
    public List<StoredChunk> nearest(String vectorLiteral, int limit) {
        return jdbc.query("""
                SELECT chunk_key, doc_slug, doc_title, heading, audience, content,
                       1 - (embedding <=> cast(? as vector)) AS similarity
                FROM knowledge_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> cast(? as vector) ASC
                LIMIT ?
                """, (rs, n) -> new StoredChunk(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getDouble(7)), vectorLiteral, vectorLiteral, limit);
    }

    public Map<String, Integer> countByDoc() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT doc_slug, count(*) FROM knowledge_chunks WHERE embedding IS NOT NULL GROUP BY doc_slug ORDER BY doc_slug",
                rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                });
        return out;
    }

    public static String toVectorLiteral(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }
}
