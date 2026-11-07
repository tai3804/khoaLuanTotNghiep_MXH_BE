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

    /** ef_search của HNSW: pgvector chỉ trả tối đa chừng này dòng cho một truy vấn, nên phải >= số ứng viên cần lấy. */
    private static final int HNSW_EF_SEARCH = 200;

    /**
     * Tìm kiếm bài viết có độ tương đồng ngữ nghĩa cao nhất bằng Cosine Similarity
     * (1 - cosine distance) trên chỉ mục HNSW.
     *
     * <p>pgvector mặc định chỉ trả tối đa {@code hnsw.ef_search = 40} dòng từ chỉ mục HNSW, nên {@code LIMIT 150} sẽ bị cắt
     * còn 40 khi bảng đủ lớn để planner dùng chỉ mục. Vì vậy đặt {@code SET LOCAL hnsw.ef_search} trong cùng transaction.
     */
    public List<PostVectorMatch> searchSimilar(List<Float> queryVector, int limit, double minSimilarity) {
        if (queryVector == null || queryVector.isEmpty() || !postSearchSupport.isVectorAvailable()) {
            return Collections.emptyList();
        }

        try {
            String vectorStr = toVectorString(queryVector);
            int safeLimit = Math.max(1, Math.min(limit, HNSW_EF_SEARCH));

            // Operator <=> trong pgvector là khoảng cách Cosine (Cosine Distance)
            // Độ tương đồng Cosine (Similarity) = 1 - Cosine Distance
            String sql = """
                SELECT post_id, (1 - (embedding <=> cast(? as vector))) AS similarity
                FROM post_embeddings
                WHERE (1 - (embedding <=> cast(? as vector))) >= ?
                ORDER BY embedding <=> cast(? as vector) ASC
                LIMIT ?
            """;
            final int finalLimit = safeLimit;

            return jdbcTemplate.execute((java.sql.Connection con) -> {
                boolean previousAutoCommit = con.getAutoCommit();
                con.setAutoCommit(false);
                try {
                    try (java.sql.Statement st = con.createStatement()) {
                        st.execute("SET LOCAL hnsw.ef_search = " + Math.max(HNSW_EF_SEARCH, finalLimit));
                    }
                    List<PostVectorMatch> out = new ArrayList<>();
                    try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setString(1, vectorStr);
                        ps.setString(2, vectorStr);
                        ps.setDouble(3, minSimilarity);
                        ps.setString(4, vectorStr);
                        ps.setInt(5, finalLimit);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                out.add(new PostVectorMatch(UUID.fromString(rs.getString("post_id")), rs.getDouble("similarity")));
                            }
                        }
                    }
                    con.commit();
                    return out;
                } catch (Exception e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(previousAutoCommit);
                }
            });

        } catch (Exception e) {
            log.error("Error executing vector similarity search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Phân bố điểm cosine của MỘT truy vấn với một mẫu ngẫu nhiên của corpus ("phân phối nền" của truy vấn đó).
     * Embedding thường dồn cụm: ở dữ liệu thật cosine giữa hai bài không liên quan đã vào khoảng 0,55–0,63 và nền này thay đổi theo
     * từng truy vấn, nên không thể dùng ngưỡng cosine cố định. Dùng trung vị và độ lệch bền vững
     * σ = (p90 − p10) / 2,563 (bằng độ lệch chuẩn nếu phân bố chuẩn, không bị kéo bởi vài bài thực sự liên quan).
     */
    public record QueryBaseline(double median, double sigma) {
        public double zScore(double cosine) {
            return (cosine - median) / sigma;
        }
    }

    private static final double MIN_BASELINE_SIGMA = 0.01;
    private static final int BASELINE_TARGET_ROWS = 400;

    public Optional<QueryBaseline> sampleQueryBaseline(List<Float> queryVector, long corpusSize) {
        if (queryVector == null || queryVector.isEmpty() || !postSearchSupport.isVectorAvailable()) {
            return Optional.empty();
        }
        try {
            double percent = corpusSize <= BASELINE_TARGET_ROWS ? 100.0
                    : Math.max(0.1, 100.0 * BASELINE_TARGET_ROWS / corpusSize);
            String sql = "SELECT (1 - (embedding <=> cast(? as vector))) FROM post_embeddings TABLESAMPLE BERNOULLI (" + percent + ")";
            List<Double> sims = jdbcTemplate.query(sql, (rs, n) -> rs.getDouble(1), toVectorString(queryVector));
            if (sims.size() < 20) {
                return Optional.empty();   // mẫu quá nhỏ để ước lượng phân bố
            }
            Collections.sort(sims);
            double median = sims.get(sims.size() / 2);
            double p10 = sims.get((int) Math.floor(sims.size() * 0.10));
            double p90 = sims.get(Math.min(sims.size() - 1, (int) Math.floor(sims.size() * 0.90)));
            double sigma = Math.max((p90 - p10) / 2.563, MIN_BASELINE_SIGMA);
            return Optional.of(new QueryBaseline(median, sigma));
        } catch (Exception e) {
            log.warn("Could not sample the query similarity baseline: {}", e.getMessage());
            return Optional.empty();
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

    private static final int QUERY_CACHE_SIZE = 500;
    private static final long QUERY_CACHE_TTL_MS = 30 * 60_000L;

    private record CachedVector(List<Float> vector, long at) {}

    private final Map<String, CachedVector> queryVectorCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedVector> eldest) {
                    return size() > QUERY_CACHE_SIZE;
                }
            });

    /**
     * Vector của một câu truy vấn tìm kiếm, có cache LRU 30 phút để các trang kết quả sau (và các lần tìm lại) không phải gọi
     * Gemini lần nữa. Chỉ cache kết quả thành công, nên lỗi tạm thời của ai-service không bị "ghi nhớ".
     */
    public List<Float> getCachedQueryEmbedding(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String key = query.trim().toLowerCase();
        long now = System.currentTimeMillis();
        CachedVector hit = queryVectorCache.get(key);
        if (hit != null && now - hit.at() < QUERY_CACHE_TTL_MS) {
            return hit.vector();
        }
        List<Float> vector = getQueryEmbedding(query);
        if (!vector.isEmpty()) {
            queryVectorCache.put(key, new CachedVector(vector, now));
        }
        return vector;
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
