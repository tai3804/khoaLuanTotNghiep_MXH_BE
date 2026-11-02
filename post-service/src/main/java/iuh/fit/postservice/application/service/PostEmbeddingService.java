package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.infrastructure.client.ai.AiServiceClient;
import iuh.fit.postservice.infrastructure.client.ai.AiServiceClient.AiBatchEmbeddingDto;
import iuh.fit.postservice.infrastructure.client.ai.AiServiceClient.AiBatchEmbeddingRequestDto;
import iuh.fit.postservice.infrastructure.client.ai.AiServiceClient.AiEmbeddingDto;
import iuh.fit.postservice.infrastructure.client.ai.AiServiceClient.AiEmbeddingRequestDto;
import iuh.fit.postservice.infrastructure.persistence.config.PostSearchSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostEmbeddingService {

    private final JdbcTemplate jdbcTemplate;
    private final PostSearchSupport postSearchSupport;
    private final AiServiceClient aiServiceClient;

    public record PostVectorMatch(UUID postId, double similarity) {}

    /**
     * Chuyển mảng List<Float> thành chuỗi định dạng PostgreSQL vector: [v1,v2,...,vn]
     */
    public String toVectorString(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Lưu trữ hoặc cập nhật vector embedding 768 chiều vào PostgreSQL pgvector
     */
    public boolean saveEmbedding(UUID postId, List<Float> vector) {
        if (postId == null || vector == null || vector.isEmpty()) {
            return false;
        }
        if (!postSearchSupport.isVectorAvailable()) {
            log.debug("pgvector is not available, skipping saveEmbedding for postId: {}", postId);
            return false;
        }

        try {
            String vectorStr = toVectorString(vector);
            String sql = """
                INSERT INTO post_embeddings (post_id, embedding, updated_at)
                VALUES (?, ?::vector, CURRENT_TIMESTAMP)
                ON CONFLICT (post_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, updated_at = CURRENT_TIMESTAMP
            """;
            int rows = jdbcTemplate.update(sql, postId, vectorStr);
            log.info("Saved embedding vector (768 dimensions) for postId: {}", postId);
            return rows > 0;
        } catch (Exception e) {
            log.error("Failed to save embedding for post {}: {}", postId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Tìm kiếm bài viết có độ tương đồng ngữ nghĩa cao nhất bằng Cosine Similarity
     * (1 - cosine distance) trên chỉ mục HNSW
     */
    public List<PostVectorMatch> searchSimilar(List<Float> queryVector, int limit, double minSimilarity) {
        if (queryVector == null || queryVector.isEmpty() || !postSearchSupport.isVectorAvailable()) {
            return Collections.emptyList();
        }

        try {
            String vectorStr = toVectorString(queryVector);
            int safeLimit = Math.max(1, Math.min(limit, 100));

            // Operator <=> trong pgvector là khoảng cách Cosine (Cosine Distance)
            // Độ tương đồng Cosine (Similarity) = 1 - Cosine Distance
            String sql = """
                SELECT post_id, (1 - (embedding <=> ?::vector)) AS similarity
                FROM post_embeddings
                WHERE (1 - (embedding <=> ?::vector)) >= ?
                ORDER BY embedding <=> ?::vector ASC
                LIMIT ?
            """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID postId = UUID.fromString(rs.getString("post_id"));
                double sim = rs.getDouble("similarity");
                return new PostVectorMatch(postId, sim);
            }, vectorStr, vectorStr, minSimilarity, vectorStr, safeLimit);

        } catch (Exception e) {
            log.error("Error executing vector similarity search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Gọi AI Service để lấy vector embedding 768 chiều cho một câu truy vấn
     */
    public List<Float> getQueryEmbedding(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ApiResponse<AiEmbeddingDto> response = aiServiceClient.generateEmbedding(new AiEmbeddingRequestDto(query.trim()));
            if (response != null && response.getData() != null && response.getData().embedding() != null) {
                return response.getData().embedding();
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding for query '{}' via ai-service: {}", query, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Tự động tạo và lưu vector embedding bất đồng bộ (Asynchronous) cho bài viết mới
     */
    public void vectorizePostAsync(UUID postId, String content) {
        if (postId == null || content == null || content.isBlank() || !postSearchSupport.isVectorAvailable()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                List<Float> vector = getQueryEmbedding(content);
                if (!vector.isEmpty()) {
                    saveEmbedding(postId, vector);
                }
            } catch (Exception e) {
                log.warn("Async vectorization skipped for post {}: {}", postId, e.getMessage());
            }
        });
    }

    /**
     * Nạp vector cho các bài viết đã tồn tại trong database nhưng chưa có vector (Backfill)
     */
    public int backfillEmbeddings(int limit) {
        if (!postSearchSupport.isVectorAvailable()) {
            log.warn("Cannot backfill embeddings: pgvector is not available.");
            return 0;
        }

        try {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            String querySql = """
                SELECT p.id, p.content
                FROM posts p
                LEFT JOIN post_embeddings pe ON p.id = pe.post_id
                WHERE pe.post_id IS NULL
                  AND p.is_deleted = false
                  AND p.status = 'PUBLISHED'
                  AND p.content IS NOT NULL
                  AND LENGTH(TRIM(p.content)) > 0
                LIMIT ?
            """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, safeLimit);
            if (rows.isEmpty()) {
                log.info("No unvectorized posts found for backfill.");
                return 0;
            }

            int successCount = 0;
            for (Map<String, Object> row : rows) {
                UUID postId = (UUID) row.get("id");
                String content = (String) row.get("content");
                if (content != null && !content.isBlank()) {
                    List<Float> vector = getQueryEmbedding(content);
                    if (!vector.isEmpty() && saveEmbedding(postId, vector)) {
                        successCount++;
                    }
                }
            }
            log.info("Successfully backfilled embeddings for {}/{} posts", successCount, rows.size());
            return successCount;

        } catch (Exception e) {
            log.error("Failed to backfill post embeddings: {}", e.getMessage(), e);
            return 0;
        }
    }
}

