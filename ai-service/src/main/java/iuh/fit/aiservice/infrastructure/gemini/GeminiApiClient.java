package iuh.fit.aiservice.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.aiservice.application.engine.FastSensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiApiClient {

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String modelName;

    @Value("${ai.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final FastSensitiveWordFilter fastFilter;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Phân tích ngữ cảnh nội dung bài viết/bình luận bằng Google Gemini 1.5 Flash
     */
    public GeminiModerationResult evaluate(String content) {
        if (content == null || content.isBlank()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("Nội dung trống")
                    .extractedKeywords(Collections.emptyList())
                    .isFallback(false)
                    .build();
        }

        // 1. Nếu chưa cấu hình Gemini API Key -> Dùng ngay Fallback Fast Filter (0ms)
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your-gemini-api-key")) {
            log.warn("Gemini API key is not configured or empty. Using Fast Rule-based fallback engine.");
            return createFallbackResult(content);
        }

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                String systemPrompt = """
                        Bạn là một AI kiểm duyệt nội dung mạng xã hội Tiếng Việt chuyên nghiệp.
                        Nhiệm vụ của bạn là phân tích văn bản và trả về JSON thuần túy (không bọc trong markdown hay ```json).
                        
                        Các tiêu chí phân tích:
                        - toxicityScore: Điểm độc hại từ 0.0 (hoàn toàn an toàn) đến 1.0 (cực kỳ độc hại/nguy hiểm).
                        - category: 1 trong các loại: NONE, PROFANITY (từ tục tĩu), HATE_SPEECH (thù ghét), HARASSMENT (quấy rối/xúc phạm), SEXUAL (18+/khiêu dâm), VIOLENCE (bạo lực/đe dọa), SPAM_SCAM (lừa đảo/cờ bạc).
                        - severity: 1 trong các mức: LOW, MEDIUM, HIGH, CRITICAL.
                        - suggestedAction: 1 trong các hành động: ALLOW (cho phép), WARN_USER (cảnh báo tác giả), AUTO_HIDE (tự động ẩn bài), DELETE_POST (xóa bài viết).
                        - reason: Giải thích ngắn gọn lý do vi phạm bằng tiếng Việt (1 câu).
                        - extractedKeywords: Mảng các từ lóng, từ ngữ nhạy cảm, xúc phạm mới được phát hiện trong bài viết (chữ thường).
                        
                        Ví dụ định dạng trả về:
                        {"toxicityScore": 0.85, "category": "HARASSMENT", "severity": "HIGH", "suggestedAction": "AUTO_HIDE", "reason": "Chứa ngôn từ thóa mạ và xúc phạm cá nhân", "extractedKeywords": ["từ_cấm_1", "từ_cấm_2"]}
                        """;

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", List.of(
                                        Map.of("text", systemPrompt + "\n\nNội dung cần phân tích:\n" + content)
                                ))
                        ),
                        "generationConfig", Map.of(
                                "temperature", 0.1,
                                "maxOutputTokens", 1024,
                                "responseMimeType", "application/json"
                        )
                );

                String requestJson = objectMapper.writeValueAsString(requestBodyMap);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseGeminiResponse(response.body());
                } else {
                    log.warn("Gemini API with model {} returned error code {}: {}. Trying next model...", candidateModel, response.statusCode(), response.body());
                }

            } catch (Exception e) {
                log.warn("Failed to call Gemini API with model {}: {}. Trying next model...", candidateModel, e.getMessage());
            }
        }

        log.error("All Gemini candidate models failed. Falling back to Fast Filter.");
        return createFallbackResult(content);
    }

    private GeminiModerationResult parseGeminiResponse(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode textNode = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            String rawJsonText = textNode.asText().trim();

            // Xóa markdown nếu có
            if (rawJsonText.startsWith("```json")) {
                rawJsonText = rawJsonText.substring(7);
            }
            if (rawJsonText.startsWith("```")) {
                rawJsonText = rawJsonText.substring(3);
            }
            if (rawJsonText.endsWith("```")) {
                rawJsonText = rawJsonText.substring(0, rawJsonText.length() - 3);
            }
            rawJsonText = rawJsonText.trim();

            JsonNode parsed = objectMapper.readTree(rawJsonText);
            double toxicityScore = parsed.path("toxicityScore").asDouble(0.0);
            String category = parsed.path("category").asText("NONE");
            String severity = parsed.path("severity").asText("LOW");
            String suggestedAction = parsed.path("suggestedAction").asText("ALLOW");
            String reason = parsed.path("reason").asText("");

            List<String> extractedKeywords = new ArrayList<>();
            JsonNode kwNode = parsed.path("extractedKeywords");
            if (kwNode.isArray()) {
                for (JsonNode item : kwNode) {
                    extractedKeywords.add(item.asText().toLowerCase().trim());
                }
            }

            boolean isToxic = toxicityScore >= 0.40 || !"NONE".equalsIgnoreCase(category);

            return GeminiModerationResult.builder()
                    .isToxic(isToxic)
                    .toxicityScore(toxicityScore)
                    .category(category)
                    .severity(severity)
                    .suggestedAction(suggestedAction)
                    .reason(reason)
                    .extractedKeywords(extractedKeywords)
                    .isFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini response text: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private GeminiModerationResult createFallbackResult(String content) {
        FastSensitiveWordFilter.FastScanResult scan = fastFilter.scan(content);

        String action = "ALLOW";
        String reason = "Nội dung đạt chuẩn cộng đồng";

        if (scan.isToxic()) {
            if ("CRITICAL".equalsIgnoreCase(scan.getHighestSeverity()) || scan.getToxicityScore() >= 0.85) {
                action = "DELETE_POST";
                reason = "Nội dung vi phạm nghiêm trọng chuẩn mực (chứa từ ngữ bạo lực, 18+ hoặc cực kỳ độc hại): " + String.join(", ", scan.getMatchedKeywords());
            } else if ("HIGH".equalsIgnoreCase(scan.getHighestSeverity()) || scan.getToxicityScore() >= 0.65) {
                action = "AUTO_HIDE";
                reason = "Bài viết bị tự động ẩn do chứa từ ngữ nhạy cảm/xúc phạm: " + String.join(", ", scan.getMatchedKeywords());
            } else {
                action = "WARN_USER";
                reason = "Bài viết chứa từ ngữ cần lưu ý chuẩn mực cộng đồng: " + String.join(", ", scan.getMatchedKeywords());
            }
        }

        return GeminiModerationResult.builder()
                .isToxic(scan.isToxic())
                .toxicityScore(scan.getToxicityScore())
                .category(scan.getPrimaryCategory())
                .severity(scan.getHighestSeverity())
                .suggestedAction(action)
                .reason(reason)
                .extractedKeywords(scan.getMatchedKeywords())
                .isFallback(true)
                .build();
    }

    public boolean hasValidApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("your-gemini-api-key");
    }

    private List<String> getCandidateModels() {
        Set<String> models = new LinkedHashSet<>();
        if (modelName != null && !modelName.isBlank() && !modelName.contains("gemini-3.5-flash")) {
            models.add(modelName.trim());
        }
        models.add("gemini-2.0-flash");
        models.add("gemini-1.5-flash");
        models.add("gemini-2.5-flash");
        models.add("gemini-1.5-pro");
        return new ArrayList<>(models);
    }

    public String generateText(String prompt, String fallbackText) {
        if (!hasValidApiKey()) {
            log.info("Gemini API key is not configured. Utilizing smart fallback engine.");
            return fallbackText;
        }

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", List.of(
                                        Map.of("text", prompt)
                                ))
                        ),
                        "generationConfig", Map.of(
                                "temperature", 0.7,
                                "maxOutputTokens", 1024
                        )
                );

                String requestJson = objectMapper.writeValueAsString(requestBodyMap);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(12))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode textNode = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                    String text = textNode.asText().trim();
                    if (!text.isBlank()) {
                        return text;
                    }
                } else {
                    log.warn("Gemini Assistant API with model {} returned code {}: {}. Trying next model...", candidateModel, response.statusCode(), response.body());
                }

            } catch (Exception e) {
                log.warn("Gemini Assistant generation error with model {}: {}. Trying next model...", candidateModel, e.getMessage());
            }
        }

        log.warn("All Gemini candidate models failed for generateText. Utilizing smart fallback engine.");
        return fallbackText;
    }
}
