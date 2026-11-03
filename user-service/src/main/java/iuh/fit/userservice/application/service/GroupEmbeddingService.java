package iuh.fit.userservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupEmbeddingService {

    private final JdbcTemplate jdbcTemplate;
    private final GroupSchemaSynchronizer groupSchemaSynchronizer;
    private final ObjectMapper objectMapper;

    @Value("${ai.service.url:http://localhost:8091}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record GroupVectorMatch(UUID groupId, double similarity) {}

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

    public boolean saveEmbedding(UUID groupId, List<Float> vector) {
        if (groupId == null || vector == null || vector.isEmpty() || !groupSchemaSynchronizer.isVectorAvailable()) {
            return false;
        }
        try {
            String vectorStr = toVectorString(vector);
            String sql = """
                INSERT INTO group_embeddings (group_id, embedding, updated_at)
                VALUES (?, cast(? as vector), CURRENT_TIMESTAMP)
                ON CONFLICT (group_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, updated_at = CURRENT_TIMESTAMP
            """;
            int rows = jdbcTemplate.update(sql, groupId, vectorStr);
            log.info("Saved embedding vector (768 dimensions) for groupId: {}", groupId);
            return rows > 0;
        } catch (Exception e) {
            log.error("Failed to save embedding for group {}: {}", groupId, e.getMessage(), e);
            return false;
        }
    }

    public List<GroupVectorMatch> searchSimilar(List<Float> queryVector, int limit, double minSimilarity) {
        if (queryVector == null || queryVector.isEmpty() || !groupSchemaSynchronizer.isVectorAvailable()) {
            return Collections.emptyList();
        }

        try {
            String vectorStr = toVectorString(queryVector);
            int safeLimit = Math.max(1, Math.min(limit, 100));

            String sql = """
                SELECT group_id, (1 - (embedding <=> cast(? as vector))) AS similarity
                FROM group_embeddings
                WHERE (1 - (embedding <=> cast(? as vector))) >= ?
                ORDER BY embedding <=> cast(? as vector) ASC
                LIMIT ?
            """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID groupId = UUID.fromString(rs.getString("group_id"));
                double sim = rs.getDouble("similarity");
                return new GroupVectorMatch(groupId, sim);
            }, vectorStr, vectorStr, minSimilarity, vectorStr, safeLimit);

        } catch (Exception e) {
            log.error("Error executing group vector similarity search: {}", e.getMessage(), e);
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
            log.warn("Failed to generate embedding for query '{}' via ai-service: {}", query, e.getMessage());
        }
        return Collections.emptyList();
    }

    public void vectorizeGroupAsync(UUID groupId, String name, String description) {
        if (groupId == null || !groupSchemaSynchronizer.isVectorAvailable()) {
            return;
        }
        String combined = (name != null ? name : "") + (description != null && !description.isBlank() ? " - " + description : "");
        if (combined.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<Float> vector = getQueryEmbedding(combined);
                if (!vector.isEmpty()) {
                    saveEmbedding(groupId, vector);
                }
            } catch (Exception e) {
                log.warn("Async vectorization skipped for group {}: {}", groupId, e.getMessage());
            }
        });
    }

    public int backfillEmbeddings(int limit) {
        if (!groupSchemaSynchronizer.isVectorAvailable()) {
            log.warn("Cannot backfill group embeddings: pgvector is not available.");
            return 0;
        }
        try {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            String querySql = """
                SELECT g.id, g.name, g.description
                FROM groups g
                LEFT JOIN group_embeddings ge ON g.id = ge.group_id
                WHERE ge.group_id IS NULL
                  AND g.is_deleted = false
                LIMIT ?
            """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, safeLimit);
            if (rows.isEmpty()) {
                log.info("No unvectorized groups found for backfill.");
                return 0;
            }

            int successCount = 0;
            for (Map<String, Object> row : rows) {
                UUID groupId = (UUID) row.get("id");
                String name = (String) row.get("name");
                String description = (String) row.get("description");
                String text = (name != null ? name : "") + (description != null && !description.isBlank() ? " - " + description : "");
                if (!text.isBlank()) {
                    List<Float> vector = getQueryEmbedding(text);
                    if (!vector.isEmpty() && saveEmbedding(groupId, vector)) {
                        successCount++;
                    }
                }
            }
            log.info("Successfully backfilled group embeddings for {}/{} groups", successCount, rows.size());
            return successCount;
        } catch (Exception e) {
            log.error("Failed to backfill group embeddings: {}", e.getMessage(), e);
            return 0;
        }
    }
}

