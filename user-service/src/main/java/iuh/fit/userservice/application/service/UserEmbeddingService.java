package iuh.fit.userservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.infrastructure.persistence.GroupSchemaSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmbeddingService {

    private final JdbcTemplate jdbcTemplate;
    private final GroupSchemaSynchronizer groupSchemaSynchronizer;
    private final ObjectMapper objectMapper;

    @Value("${ai.service.url:http://localhost:8091}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record UserVectorMatch(UUID userId, double similarity) {}

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

    public boolean saveEmbedding(UUID userId, List<Float> vector) {
        if (userId == null || vector == null || vector.isEmpty() || !groupSchemaSynchronizer.isVectorAvailable()) {
            return false;
        }
        try {
            String vectorStr = toVectorString(vector);
            String sql = """
                INSERT INTO user_embeddings (user_id, embedding, updated_at)
                VALUES (?, cast(? as vector), CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, updated_at = CURRENT_TIMESTAMP
            """;
            int rows = jdbcTemplate.update(sql, userId, vectorStr);
            log.info("Saved user embedding vector (768 dimensions) for userId: {}", userId);
            return rows > 0;
        } catch (Exception e) {
            log.error("Failed to save embedding for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    public List<UserVectorMatch> searchSimilar(List<Float> queryVector, int limit, double minSimilarity) {
        if (queryVector == null || queryVector.isEmpty() || !groupSchemaSynchronizer.isVectorAvailable()) {
            return Collections.emptyList();
        }

        try {
            String vectorStr = toVectorString(queryVector);
            int safeLimit = Math.max(1, Math.min(limit, 100));

            String sql = """
                SELECT user_id, (1 - (embedding <=> cast(? as vector))) AS similarity
                FROM user_embeddings
                WHERE (1 - (embedding <=> cast(? as vector))) >= ?
                ORDER BY embedding <=> cast(? as vector) ASC
                LIMIT ?
            """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID userId = UUID.fromString(rs.getString("user_id"));
                double sim = rs.getDouble("similarity");
                return new UserVectorMatch(userId, sim);
            }, vectorStr, vectorStr, minSimilarity, vectorStr, safeLimit);

        } catch (Exception e) {
            log.error("Error executing user vector similarity search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<UserVectorMatch> findSimilarUsers(UUID currentUserId, int limit, double minSimilarity) {
        if (currentUserId == null || !groupSchemaSynchronizer.isVectorAvailable()) {
            return Collections.emptyList();
        }
        try {
            int safeLimit = Math.max(1, Math.min(limit, 50));
            String sql = """
                SELECT u2.user_id, (1 - (u2.embedding <=> u1.embedding)) AS similarity
                FROM user_embeddings u1
                JOIN user_embeddings u2 ON u1.user_id != u2.user_id
                WHERE u1.user_id = ?
                  AND (1 - (u2.embedding <=> u1.embedding)) >= ?
                ORDER BY u2.embedding <=> u1.embedding ASC
                LIMIT ?
            """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID userId = UUID.fromString(rs.getString("user_id"));
                double sim = rs.getDouble("similarity");
                return new UserVectorMatch(userId, sim);
            }, currentUserId, minSimilarity, safeLimit);

        } catch (Exception e) {
            log.error("Error finding similar users for {}: {}", currentUserId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<Float> getQueryEmbedding(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = aiServiceUrl + "/api/v1/ai/embeddings/generate";
            Map<String, String> body = Map.of("text", query.trim());
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode arr = root.path("data").path("embedding");
                if (arr.isArray()) {
                    List<Float> vec = new ArrayList<>(arr.size());
                    for (JsonNode n : arr) {
                        vec.add((float) n.asDouble());
                    }
                    return vec;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate user embedding for query '{}' via ai-service: {}", query, e.getMessage());
        }
        return Collections.emptyList();
    }

    public void vectorizeUserProfileAsync(UUID userId, UserProfile profile) {
        if (userId == null || profile == null || !groupSchemaSynchronizer.isVectorAvailable()) {
            return;
        }

        String fullName = Stream.of(profile.getLastName(), profile.getMiddleName(), profile.getFirstName())
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        StringBuilder sb = new StringBuilder(fullName);
        if (profile.getBio() != null && !profile.getBio().isBlank()) {
            sb.append(" | Bio: ").append(profile.getBio());
        }
        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            sb.append(" | Địa điểm: ").append(profile.getLocation());
        }
        if (profile.getCreatorCategory() != null && !profile.getCreatorCategory().isBlank()) {
            sb.append(" | Lĩnh vực: ").append(profile.getCreatorCategory());
        }
        if (profile.getWebsite() != null && !profile.getWebsite().isBlank()) {
            sb.append(" | Website: ").append(profile.getWebsite());
        }

        String combined = sb.toString().trim();
        if (combined.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<Float> vector = getQueryEmbedding(combined);
                if (!vector.isEmpty()) {
                    saveEmbedding(userId, vector);
                }
            } catch (Exception e) {
                log.warn("Async vectorization skipped for user {}: {}", userId, e.getMessage());
            }
        });
    }

    public int backfillEmbeddings(int limit) {
        if (!groupSchemaSynchronizer.isVectorAvailable()) {
            log.warn("Cannot backfill user embeddings: pgvector is not available.");
            return 0;
        }
        try {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            String querySql = """
                SELECT p.user_id, p.first_name, p.last_name, p.middle_name, p.bio, p.location, p.creator_category, p.website
                FROM user_profiles p
                LEFT JOIN user_embeddings ue ON p.user_id = ue.user_id
                WHERE ue.user_id IS NULL
                  AND p.is_deleted = false
                LIMIT ?
            """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, safeLimit);
            if (rows.isEmpty()) {
                log.info("No unvectorized user profiles found for backfill.");
                return 0;
            }

            int successCount = 0;
            for (Map<String, Object> row : rows) {
                UUID userId = (UUID) row.get("user_id");
                String fn = (String) row.get("first_name");
                String ln = (String) row.get("last_name");
                String mn = (String) row.get("middle_name");
                String bio = (String) row.get("bio");
                String loc = (String) row.get("location");
                String cat = (String) row.get("creator_category");

                String fullName = Stream.of(ln, mn, fn).filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(" "));
                StringBuilder sb = new StringBuilder(fullName);
                if (bio != null && !bio.isBlank()) sb.append(" | Bio: ").append(bio);
                if (loc != null && !loc.isBlank()) sb.append(" | Địa điểm: ").append(loc);
                if (cat != null && !cat.isBlank()) sb.append(" | Lĩnh vực: ").append(cat);

                String text = sb.toString().trim();
                if (!text.isBlank()) {
                    List<Float> vector = getQueryEmbedding(text);
                    if (!vector.isEmpty() && saveEmbedding(userId, vector)) {
                        successCount++;
                    }
                }
            }
            log.info("Successfully backfilled user embeddings for {}/{} users", successCount, rows.size());
            return successCount;
        } catch (Exception e) {
            log.error("Failed to backfill user embeddings: {}", e.getMessage(), e);
            return 0;
        }
    }
}

