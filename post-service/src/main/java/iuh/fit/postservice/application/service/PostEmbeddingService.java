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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostEmbeddingService {

    private final JdbcTemplate jdbcTemplate;
    private final PostSearchSupport postSearchSupport;
    private final AiServiceClient aiServiceClient;

    public record PostVectorMatch(UUID postId, double similarity) {}
    public record UserSearchInterest(String queryText, String vectorStr) {}

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
                VALUES (?, cast(? as vector), CURRENT_TIMESTAMP)
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
                SELECT post_id, (1 - (embedding <=> cast(? as vector))) AS similarity
                FROM post_embeddings
                WHERE (1 - (embedding <=> cast(? as vector))) >= ?
                ORDER BY embedding <=> cast(? as vector) ASC
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
     * Tìm các bài viết có ngữ nghĩa tương tự với một bài viết cụ thể (Dùng cho tính năng Đề xuất / Bài viết liên quan)
     */
    public List<PostVectorMatch> findRelatedPosts(UUID postId, int limit, double minSimilarity) {
        if (postId == null || !postSearchSupport.isVectorAvailable()) {
            return Collections.emptyList();
        }

        try {
            int safeLimit = Math.max(1, Math.min(limit, 50));
            String sql = """
                SELECT p2.post_id, (1 - (p2.embedding <=> p1.embedding)) AS similarity
                FROM post_embeddings p1
                JOIN post_embeddings p2 ON p1.post_id != p2.post_id
                WHERE p1.post_id = ?
                  AND (1 - (p2.embedding <=> p1.embedding)) >= ?
                ORDER BY p2.embedding <=> p1.embedding ASC
                LIMIT ?
            """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID matchedPostId = UUID.fromString(rs.getString("post_id"));
                double sim = rs.getDouble("similarity");
                return new PostVectorMatch(matchedPostId, sim);
            }, postId, minSimilarity, safeLimit);

        } catch (Exception e) {
            log.error("Error finding related posts for {}: {}", postId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

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

    /**
     * Lưu trữ ý định tìm kiếm gần nhất của người dùng vào bảng user_search_interests
     */
    public void saveUserSearchInterest(UUID userId, String queryText, List<Float> vector) {
        if (userId == null || queryText == null || queryText.isBlank() || vector == null || vector.isEmpty() || !postSearchSupport.isVectorAvailable()) {
            return;
        }
        try {
            String vectorStr = toVectorString(vector);
            String sql = """
                INSERT INTO user_search_interests (user_id, query_text, embedding, updated_at)
                VALUES (?, ?, cast(? as vector), CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE
                SET query_text = EXCLUDED.query_text, embedding = EXCLUDED.embedding, updated_at = CURRENT_TIMESTAMP
            """;
            jdbcTemplate.update(sql, userId, queryText.trim(), vectorStr);
            log.info("Saved search interest for user {}: '{}'", userId, queryText.trim());
        } catch (Exception e) {
            log.warn("Failed to save search interest for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Lấy ý định tìm kiếm gần nhất của người dùng trong vòng N giờ
     */
    public Optional<UserSearchInterest> getRecentSearchInterest(UUID userId, int withinHours) {
        if (userId == null || !postSearchSupport.isVectorAvailable()) {
            return Optional.empty();
        }
        try {
            String sql = """
                SELECT query_text, cast(embedding as text) as vec
                FROM user_search_interests
                WHERE user_id = ?
                  AND updated_at >= NOW() - (? * INTERVAL '1 hour')
                ORDER BY updated_at DESC
                LIMIT 1
            """;
            List<UserSearchInterest> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                String q = rs.getString("query_text");
                String vec = rs.getString("vec");
                return new UserSearchInterest(q, vec);
            }, userId, withinHours);

            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            log.warn("Failed to get search interest for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tính toán độ tương đồng ngữ nghĩa giữa vector tìm kiếm và danh sách bài viết ứng viên trên Bảng tin
     */
    public Map<UUID, Double> computeSimilaritiesForPosts(List<UUID> postIds, String vectorStr) {
        if (postIds == null || postIds.isEmpty() || vectorStr == null || vectorStr.isBlank() || !postSearchSupport.isVectorAvailable()) {
            return Collections.emptyMap();
        }
        try {
            String inPlaceholders = String.join(",", Collections.nCopies(postIds.size(), "?::uuid"));
            String sql = String.format("""
                SELECT post_id, (1 - (embedding <=> cast(? as vector))) AS similarity
                FROM post_embeddings
                WHERE post_id IN (%s)
            """, inPlaceholders);

            List<Object> params = new ArrayList<>(postIds.size() + 1);
            params.add(vectorStr);
            for (UUID id : postIds) {
                params.add(id.toString());
            }

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID pid = UUID.fromString(rs.getString("post_id"));
                double sim = rs.getDouble("similarity");
                return Map.entry(pid, sim);
            }, params.toArray()).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        } catch (Exception e) {
            log.warn("Failed to compute similarities for candidate posts: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
