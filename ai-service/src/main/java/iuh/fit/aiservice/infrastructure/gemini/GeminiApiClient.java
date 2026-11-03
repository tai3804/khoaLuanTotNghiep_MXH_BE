package iuh.fit.aiservice.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.aiservice.application.dto.MessageSummaryDto;
import iuh.fit.aiservice.application.engine.FastSensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
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

    @Value("${ai.gemini.model:gemini-2.5-flash-lite}")
    private String modelName;

    @Value("${ai.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final FastSensitiveWordFilter fastFilter;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * PhĂ¢n tĂ­ch ngá»¯ cáº£nh ná»™i dung bĂ i viáº¿t/bĂ¬nh luáº­n báº±ng Google Gemini Flash
     */
    public GeminiModerationResult evaluate(String content) {
        if (content == null || content.isBlank()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("Ná»™i dung trá»‘ng")
                    .extractedKeywords(Collections.emptyList())
                    .isFallback(false)
                    .build();
        }

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your-gemini-api-key")) {
            log.warn("Gemini API key is not configured or empty. Using Fast Rule-based fallback engine.");
            return createFallbackResult(content);
        }

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                String systemPrompt = """
                        Báº¡n lĂ  má»™t AI kiá»ƒm duyá»‡t ná»™i dung máº¡ng xĂ£ há»™i Tiáº¿ng Viá»‡t thĂ´ng minh vĂ  cĂ´ng tĂ¢m.
                        Nhiá»‡m vá»¥ cá»§a báº¡n lĂ  phĂ¢n tĂ­ch vÄƒn báº£n bĂ i viáº¿t hoáº·c bĂ¬nh luáº­n vĂ  tráº£ vá» káº¿t quáº£ JSON thuáº§n tĂºy (khĂ´ng bá»c trong markdown hay ```json).
                        
                        Quy táº¯c nháº­n diá»‡n quan trá»ng (TRĂNH Báº®T OAN):
                        1. TUYá»†T Äá»I KHĂ”NG Ä‘Ă¡nh giĂ¡ Ä‘á»™c háº¡i cĂ¡c tháº» gáº¯n tĂªn ngÆ°á»i dĂ¹ng (vĂ­ dá»¥: @Äiá»m, @Äiá»ƒm, @Duy, @Dung, @Huá»‡, @[TĂªn Báº¡n]...).
                        2. TUYá»†T Äá»I KHĂ”NG nháº§m láº«n cĂ¡c tá»« ngá»¯ ngá»¯ phĂ¡p hoáº·c tá»« vá»±ng lĂ nh máº¡nh thĂ´ng dá»¥ng tiáº¿ng Viá»‡t (Ä‘iá»ƒm sá»‘, Ä‘iá»m bĂ¡o, Ä‘á»‹a Ä‘iá»ƒm, giĂ¡o dá»¥c, du lá»‹ch, nguá»“n gá»‘c, con ong, cĂ¡i lon, cĂ¡c báº¡n...) vá»›i tá»« cáº¥m/tá»¥c tÄ©u.
                        3. Chá»‰ xá»­ lĂ½ vi pháº¡m khi cĂ³ hĂ nh vi chá»­i bá»›i, lÄƒng máº¡, xĂºc pháº¡m danh dá»± ngÆ°á»i khĂ¡c, Ä‘e dá»a báº¡o lá»±c, ná»™i dung 18+ Ä‘á»“i trá»¥y hoáº·c lá»«a Ä‘áº£o cá» báº¡c rĂµ rĂ ng.
                        
                        CĂ¡c trÆ°á»ng káº¿t quáº£ JSON:
                        - toxicityScore: Äiá»ƒm Ä‘á»™c háº¡i tá»« 0.0 (hoĂ n toĂ n an toĂ n) Ä‘áº¿n 1.0 (cá»±c ká»³ Ä‘á»™c háº¡i/nguy hiá»ƒm). Náº¿u ná»™i dung bĂ¬nh thÆ°á»ng, hĂ£y tráº£ vá» 0.0.
                        - category: 1 trong cĂ¡c loáº¡i: NONE, PROFANITY (tá»« tá»¥c tÄ©u), HATE_SPEECH (thĂ¹ ghĂ©t), HARASSMENT (quáº¥y rá»‘i/xĂºc pháº¡m), SEXUAL (18+/khiĂªu dĂ¢m), VIOLENCE (báº¡o lá»±c/Ä‘e dá»a), SPAM_SCAM (lá»«a Ä‘áº£o/cá» báº¡c).
                        - severity: 1 trong cĂ¡c má»©c: LOW, MEDIUM, HIGH, CRITICAL. (Náº¿u an toĂ n, Ä‘á»ƒ LOW).
                        - suggestedAction: 1 trong cĂ¡c hĂ nh Ä‘á»™ng: ALLOW (cho phĂ©p), WARN_USER (cáº£nh bĂ¡o tĂ¡c giáº£), AUTO_HIDE (tá»± Ä‘á»™ng áº©n bĂ i), DELETE_POST (xĂ³a bĂ i viáº¿t). (Náº¿u an toĂ n, luĂ´n lĂ  ALLOW).
                        - reason: Giáº£i thĂ­ch ngáº¯n gá»n lĂ½ do báº±ng tiáº¿ng Viá»‡t (1 cĂ¢u, dĂ¹ng tá»« 'Ná»™i dung bĂ i viáº¿t' hoáº·c 'BĂ i viáº¿t', vĂ­ dá»¥: 'Ná»™i dung bĂ i viáº¿t chá»©a tá»« ngá»¯ thĂ´ tá»¥c, xĂºc pháº¡m'). Tuyá»‡t Ä‘á»‘i khĂ´ng dĂ¹ng tá»« 'BĂ¬nh luáº­n' khi giáº£i thĂ­ch bĂ i viáº¿t.
                        - extractedKeywords: Máº£ng cĂ¡c tá»« ngá»¯ nháº¡y cáº£m thá»±c sá»± cĂ³ trong bĂ i viáº¿t (chá»¯ thÆ°á»ng), Ä‘á»ƒ rá»—ng [] náº¿u bĂ i viáº¿t an toĂ n.
                        
                        VĂ­ dá»¥ Ä‘á»‹nh dáº¡ng an toĂ n:
                        {"toxicityScore": 0.0, "category": "NONE", "severity": "LOW", "suggestedAction": "ALLOW", "reason": "Ná»™i dung an toĂ n, gáº¯n tháº» báº¡n bĂ¨ bĂ¬nh thÆ°á»ng", "extractedKeywords": []}
                        """;

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", List.of(
                                        Map.of("text", systemPrompt + "\n\nNá»™i dung cáº§n phĂ¢n tĂ­ch:\n" + content)
                                ))
                        ),
                        "generationConfig", Map.of(
                                "temperature", 0.1,
                                "maxOutputTokens", 2048,
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

            // Kiá»ƒm tra safety block á»Ÿ má»©c promptFeedback
            JsonNode promptFeedback = rootNode.path("promptFeedback");
            String blockReason = promptFeedback.path("blockReason").asText("");
            if (!blockReason.isBlank()) {
                return GeminiModerationResult.builder()
                        .isToxic(true)
                        .toxicityScore(0.95)
                        .category("SAFETY_BLOCK")
                        .severity("CRITICAL")
                        .suggestedAction("AUTO_HIDE")
                        .reason("Ná»™i dung bá»‹ cháº·n bá»Ÿi chĂ­nh sĂ¡ch an toĂ n AI: " + blockReason)
                        .extractedKeywords(Collections.emptyList())
                        .isFallback(false)
                        .build();
            }

            JsonNode candidates = rootNode.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return createFallbackResult(responseBody);
            }

            JsonNode candidate = candidates.get(0);
            String finishReason = candidate.path("finishReason").asText("");
            if ("SAFETY".equalsIgnoreCase(finishReason) || "PROHIBITED_CONTENT".equalsIgnoreCase(finishReason) || "BLOCKLIST".equalsIgnoreCase(finishReason) || "SPII".equalsIgnoreCase(finishReason)) {
                return GeminiModerationResult.builder()
                        .isToxic(true)
                        .toxicityScore(0.95)
                        .category("SAFETY_BLOCK")
                        .severity("CRITICAL")
                        .suggestedAction("AUTO_HIDE")
                        .reason("Ná»™i dung vi pháº¡m chĂ­nh sĂ¡ch an toĂ n (" + finishReason + ")")
                        .extractedKeywords(Collections.emptyList())
                        .isFallback(false)
                        .build();
            }

            String rawJsonText = extractTextFromCandidate(candidate);

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

            int firstBrace = rawJsonText.indexOf('{');
            int lastBrace = rawJsonText.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                rawJsonText = rawJsonText.substring(firstBrace, lastBrace + 1);
            }

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
            return createFallbackResult(responseBody);
        }
    }

    private GeminiModerationResult createFallbackResult(String content) {
        FastSensitiveWordFilter.FastScanResult scan = fastFilter.scan(content);

        String action = "ALLOW";
        String reason = "Ná»™i dung an toĂ n, phĂ¹ há»£p vá»›i tiĂªu chuáº©n cá»™ng Ä‘á»“ng.";

        if (scan.isToxic()) {
            String keywordsStr = String.join(", ", scan.getMatchedKeywords());
            String category = scan.getPrimaryCategory() != null ? scan.getPrimaryCategory() : "PROFANITY";

            String categoryDesc = switch (category.toUpperCase()) {
                case "PROFANITY" -> "sá»­ dá»¥ng tá»« ngá»¯ dung tá»¥c hoáº·c thiáº¿u chuáº©n má»±c";
                case "HATE_SPEECH" -> "chá»©a ngĂ´n tá»« kĂ­ch Ä‘á»™ng, phĂ¢n biá»‡t hoáº·c thĂ¹ ghĂ©t";
                case "HARASSMENT" -> "cĂ³ dáº¥u hiá»‡u cĂ´ng kĂ­ch, quáº¥y rá»‘i hoáº·c xĂºc pháº¡m danh dá»±";
                case "VIOLENCE" -> "chá»©a ná»™i dung báº¡o lá»±c hoáº·c Ä‘e dá»a an toĂ n cĂ¡ nhĂ¢n";
                case "SPAM_SCAM" -> "cĂ³ dáº¥u hiá»‡u quáº£ng cĂ¡o rĂ¡c, lá»«a Ä‘áº£o hoáº·c cá» báº¡c";
                case "SEXUAL" -> "chá»©a ná»™i dung 18+ nháº¡y cáº£m hoáº·c khiĂªu dĂ¢m";
                default -> "chá»©a tá»« ngá»¯ vi pháº¡m quy táº¯c á»©ng xá»­ vÄƒn minh";
            };

            if ("CRITICAL".equalsIgnoreCase(scan.getHighestSeverity()) || scan.getToxicityScore() >= 0.85) {
                action = "DELETE_POST";
                reason = String.format("Vi pháº¡m nghiĂªm trá»ng chuáº©n má»±c cá»™ng Ä‘á»“ng do %s (tá»« khĂ³a: %s). Há»‡ thá»‘ng Ä‘Ă£ tá»± Ä‘á»™ng gá»¡ bá» Ä‘á»ƒ báº£o vá»‡ an toĂ n cho thĂ nh viĂªn.", categoryDesc, keywordsStr);
            } else {
                action = "AUTO_HIDE";
                reason = String.format("BĂ i viáº¿t Ä‘Ă£ bá»‹ táº¡m áº©n do %s (tá»« khĂ³a: %s). Báº¡n cĂ³ thá»ƒ chá»‰nh sá»­a láº¡i bĂ i viáº¿t Ä‘á»ƒ phĂ¹ há»£p vá»›i vÄƒn hĂ³a máº¡ng xĂ£ há»™i.", categoryDesc, keywordsStr);
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

    private String extractTextFromCandidate(JsonNode candidate) {
        if (candidate == null) return "";
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return candidate.path("content").path("text").asText("");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("thought") && part.get("thought").asBoolean(false)) {
                continue;
            }
            if (part.has("text")) {
                sb.append(part.get("text").asText());
            }
        }
        if (sb.isEmpty()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
        }
        return sb.toString().trim();
    }

    private List<String> getCandidateModels() {
        Set<String> models = new LinkedHashSet<>();
        if (modelName != null && !modelName.isBlank()) {
            models.add(modelName.trim());
        }
        models.add("gemini-2.5-flash-lite");
        models.add("gemini-2.0-flash");
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
                                "maxOutputTokens", 2048
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
                    JsonNode candidates = rootNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode candidate = candidates.get(0);
                        if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
                            log.warn("Gemini model {} stopped at MAX_TOKENS. Trying next model...", candidateModel);
                            continue;
                        }
                        String text = extractTextFromCandidate(candidate);
                        if (!text.isBlank()) {
                            return text;
                        }
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

    /**
     * TrĂ² chuyá»‡n Ä‘a lÆ°á»£t cĂ³ nhá»› ngá»¯ cáº£nh (Multi-turn Contextual Conversation)
     */
    public String chatWithHistory(String currentMessage, List<?> rawHistory, String fallbackText) {
        if (!hasValidApiKey()) {
            return fallbackText;
        }

        List<Map<String, Object>> contents = new ArrayList<>();

        if (rawHistory != null && !rawHistory.isEmpty()) {
            int startIdx = Math.max(0, rawHistory.size() - 10);
            for (int i = startIdx; i < rawHistory.size(); i++) {
                Object item = rawHistory.get(i);
                try {
                    String role = "user";
                    String text = "";
                    if (item instanceof Map<?, ?> map) {
                        Object senderObj = map.get("sender");
                        String sender = senderObj != null ? senderObj.toString() : "user";
                        role = ("ai".equalsIgnoreCase(sender) || "model".equalsIgnoreCase(sender) || "assistant".equalsIgnoreCase(sender)) ? "model" : "user";
                        Object textObj = map.get("text");
                        text = textObj != null ? textObj.toString() : "";
                    } else {
                        JsonNode node = objectMapper.valueToTree(item);
                        String sender = node.path("sender").asText(node.path("role").asText("user"));
                        role = ("ai".equalsIgnoreCase(sender) || "model".equalsIgnoreCase(sender) || "assistant".equalsIgnoreCase(sender)) ? "model" : "user";
                        text = node.path("text").asText(node.path("content").asText(""));
                    }

                    if (!text.isBlank()) {
                        contents.add(Map.of(
                                "role", role,
                                "parts", List.of(Map.of("text", text))
                        ));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", currentMessage))
        ));

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                Map<String, Object> requestBodyMap = new LinkedHashMap<>();
                requestBodyMap.put("system_instruction", Map.of(
                        "parts", List.of(Map.of("text", """
                                Báº¡n lĂ  Trá»£ lĂ½ AI KLTN Social thĂ´ng minh, thĂ¢n thiá»‡n, Ä‘á»“ng hĂ nh cĂ¹ng sinh viĂªn vĂ  giá»›i tráº».
                                Phong cĂ¡ch giao tiáº¿p:
                                - Tráº£ lá»i sĂºc tĂ­ch, tá»± nhiĂªn, vÄƒn minh, cĂ³ emoji phĂ¹ há»£p nhÆ°ng khĂ´ng láº¡m dá»¥ng.
                                - Äi tháº³ng vĂ o trá»ng tĂ¢m cĂ¢u há»i cá»§a ngÆ°á»i dĂ¹ng, TUYá»†T Äá»I khĂ´ng láº·p láº¡i cĂ¢u chĂ o khuĂ´n máº«u á»Ÿ má»—i lÆ°á»£t tráº£ lá»i.
                                - Há»— trá»£ chuyĂªn sĂ¢u vá»: Láº­p trĂ¬nh (Spring Boot, React, Microservices...), Há»c táº­p, LĂ m Ä‘á»“ Ă¡n KLTN, SĂ¡ng táº¡o bĂ i viáº¿t, Giáº£i tá»a Ă¡p lá»±c.
                                - Sá»­ dá»¥ng Markdown Ä‘áº¹p máº¯t (gáº¡ch Ä‘áº§u dĂ²ng, in Ä‘áº­m tá»« khĂ³a quan trá»ng).
                                """))
                ));
                requestBodyMap.put("contents", contents);
                requestBodyMap.put("generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 2048
                ));

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
                    JsonNode candidates = rootNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode candidate = candidates.get(0);
                        String text = extractTextFromCandidate(candidate);
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                } else {
                    log.warn("Gemini chatWithHistory model {} error code {}: {}. Trying next...", candidateModel, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.warn("Gemini chatWithHistory error with model {}: {}. Trying next...", candidateModel, e.getMessage());
            }
        }

        return fallbackText;
    }

    /**
     * Táº£i áº£nh tá»« URL chuyá»ƒn thĂ nh inline_data Base64 cho Gemini Multimodal Vision (ngÄƒn SSRF + giá»›i háº¡n 10MB)
     */
    private Optional<Map<String, Object>> fetchImageInlinePart(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("http")) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(imageUrl.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    String hostLower = host.toLowerCase();
                    if (!hostLower.contains("minio") && !hostLower.contains("localhost")) {
                        log.warn("Blocked potentially unsafe host IP address in fetchImageInlinePart: {}", addr);
                        return Optional.empty();
                    }
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    byte[] bytes = is.readNBytes(10 * 1024 * 1024); // max 10MB
                    if (bytes.length > 0) {
                        String mimeType = "image/jpeg";
                        String contentType = response.headers().firstValue("Content-Type").orElse("");
                        if (contentType.contains("png") || imageUrl.endsWith(".png")) {
                            mimeType = "image/png";
                        } else if (contentType.contains("webp") || imageUrl.endsWith(".webp")) {
                            mimeType = "image/webp";
                        }

                        String base64Data = Base64.getEncoder().encodeToString(bytes);
                        return Optional.of(Map.of(
                                "inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", base64Data
                                )
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not download image for multimodal summary: {} ({})", imageUrl, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * TĂ³m táº¯t cuá»™c trĂ² chuyá»‡n Äa phÆ°Æ¡ng thá»©c (Multimodal: Text + HĂ¬nh áº£nh) báº±ng Gemini
     */
    public MessageSummaryDto.SummarizeMessagesResponse summarizeMessagesWithGemini(
            MessageSummaryDto.SummarizeMessagesRequest request,
            MessageSummaryDto.SummarizeMessagesResponse fallback) {

        if (!hasValidApiKey() || request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return fallback;
        }

        List<MessageSummaryDto.MessageItem> messages = request.getMessages();
        String convName = request.getConversationName() != null ? request.getConversationName() : "Cuá»™c trĂ² chuyá»‡n";
        boolean isGroup = Boolean.TRUE.equals(request.getIsGroup());

        StringBuilder chatTranscript = new StringBuilder();
        chatTranscript.append(String.format("TĂªn cuá»™c há»™i thoáº¡i: %s (%s)\n", convName, isGroup ? "NhĂ³m chat" : "Chat 1-1"));
        chatTranscript.append("Danh sĂ¡ch cĂ¡c tin nháº¯n gáº§n nháº¥t:\n");

        List<String> imageUrlsToFetch = new ArrayList<>();
        int imageCount = 0;

        for (MessageSummaryDto.MessageItem item : messages) {
            String sender = item.getSenderName() != null ? item.getSenderName() : "ThĂ nh viĂªn";
            String time = item.getTime() != null ? item.getTime() : "";
            String text = item.getText() != null ? item.getText() : "";
            String media = item.getMediaUrl() != null ? item.getMediaUrl() : "";

            boolean isImage = (media.contains(".jpg") || media.contains(".jpeg") || media.contains(".png") || media.contains(".webp") || media.contains("images"))
                    || (text.startsWith("http") && (text.contains(".jpg") || text.contains(".jpeg") || text.contains(".png") || text.contains(".webp")));

            if (isImage) {
                imageCount++;
                String imgUrl = !media.isBlank() ? media : text;
                if (imageUrlsToFetch.size() < 3 && imgUrl.startsWith("http")) {
                    imageUrlsToFetch.add(imgUrl);
                }
                chatTranscript.append(String.format("[%s] %s: [ÄĂƒ Gá»¬I 1 HĂŒNH áº¢NH ÄĂNH KĂˆM]\n", time, sender));
            } else {
                chatTranscript.append(String.format("[%s] %s: %s\n", time, sender, text));
            }
        }

        List<Map<String, Object>> parts = new ArrayList<>();

        String systemInstruction = """
                Báº¡n lĂ  Trá»£ lĂ½ AI phĂ¢n tĂ­ch vĂ  tĂ³m táº¯t cuá»™c trĂ² chuyá»‡n máº¡ng xĂ£ há»™i (há»— trá»£ cáº£ vÄƒn báº£n vĂ  hĂ¬nh áº£nh Ä‘Ă­nh kĂ¨m).
                Nhiá»‡m vá»¥ cá»§a báº¡n:
                1. Äá»c ká»¹ dĂ²ng thá»i gian tin nháº¯n vĂ  quan sĂ¡t trá»±c tiáº¿p cĂ¡c hĂ¬nh áº£nh Ä‘Ă­nh kĂ¨m (náº¿u cĂ³).
                2. TĂ³m táº¯t ná»™i dung chĂ­nh xĂ¡c, ngáº¯n gá»n, sĂºc tĂ­ch (1-2 Ä‘oáº¡n).
                3. MĂ´ táº£ ná»™i dung hĂ¬nh áº£nh Ä‘Ă­nh kĂ¨m (áº£nh chá»¥p bĂ i há»c, slide, tĂ i liá»‡u, sÆ¡ Ä‘á»“, áº£nh Ä‘á»i thÆ°á»ng...).
                4. TrĂ­ch xuáº¥t danh sĂ¡ch cĂ¡c viá»‡c cáº§n lĂ m, deadline, lá»‹ch háº¹n (actionItems).
                
                Äá»‹nh dáº¡ng tráº£ vá» Báº®T BUá»˜C lĂ  JSON thuáº§n tĂºy (khĂ´ng bá»c trong markdown ```json):
                {
                  "summary": "TĂ³m táº¯t ngáº¯n gá»n cĂ¡c váº¥n Ä‘á» chĂ­nh Ä‘Ă£ tháº£o luáº­n...",
                  "mediaDescription": "MĂ´ táº£ chi tiáº¿t ná»™i dung cĂ¡c áº£nh Ä‘Ă­nh kĂ¨m (hoáº·c 'KhĂ´ng cĂ³ hĂ¬nh áº£nh Ä‘Ă­nh kĂ¨m' náº¿u khĂ´ng cĂ³)...",
                  "actionItems": ["Viá»‡c 1 cáº§n lĂ m (kĂ¨m ngÆ°á»i phá»¥ trĂ¡ch / deadline náº¿u cĂ³)", "Viá»‡c 2..."]
                }
                """;

        parts.add(Map.of("text", systemInstruction + "\n\n" + chatTranscript.toString()));

        for (String imgUrl : imageUrlsToFetch) {
            fetchImageInlinePart(imgUrl).ifPresent(parts::add);
        }

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", parts)
                        ),
                        "generationConfig", Map.of(
                                "temperature", 0.2,
                                "maxOutputTokens", 2048,
                                "responseMimeType", "application/json"
                        )
                );

                String requestJson = objectMapper.writeValueAsString(requestBodyMap);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode candidates = rootNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode candidate = candidates.get(0);
                        String rawJsonText = extractTextFromCandidate(candidate);

                        if (rawJsonText.startsWith("```json")) rawJsonText = rawJsonText.substring(7);
                        if (rawJsonText.startsWith("```")) rawJsonText = rawJsonText.substring(3);
                        if (rawJsonText.endsWith("```")) rawJsonText = rawJsonText.substring(0, rawJsonText.length() - 3);
                        rawJsonText = rawJsonText.trim();

                        int firstBrace = rawJsonText.indexOf('{');
                        int lastBrace = rawJsonText.lastIndexOf('}');
                        if (firstBrace >= 0 && lastBrace > firstBrace) {
                            rawJsonText = rawJsonText.substring(firstBrace, lastBrace + 1);
                        }

                        JsonNode parsed = objectMapper.readTree(rawJsonText);
                        String summary = parsed.path("summary").asText(fallback.getSummary());
                        String mediaDesc = parsed.path("mediaDescription").asText(fallback.getMediaDescription());
                        List<String> actionItems = new ArrayList<>();
                        JsonNode actionsNode = parsed.path("actionItems");
                        if (actionsNode.isArray()) {
                            for (JsonNode node : actionsNode) {
                                actionItems.add(node.asText());
                            }
                        }
                        if (actionItems.isEmpty()) {
                            actionItems = fallback.getActionItems();
                        }

                        return MessageSummaryDto.SummarizeMessagesResponse.builder()
                                .summary(summary)
                                .mediaDescription(mediaDesc)
                                .actionItems(actionItems)
                                .messageCount(messages.size())
                                .imageCount(imageCount)
                                .build();
                    }
                } else {
                    log.warn("Gemini multimodal summarize returned {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.warn("Gemini multimodal summarize error with model {}: {}", candidateModel, e.getMessage());
            }
        }

        return fallback;
    }

    /**
     * Kiá»ƒm duyá»‡t hĂ¬nh áº£nh Ä‘a phÆ°Æ¡ng thá»©c (Multimodal Vision Moderation) báº±ng Google Gemini Vision
     */
    public GeminiModerationResult evaluateImageWithVision(String imageUrl, String caption) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("KhĂ´ng cĂ³ hĂ¬nh áº£nh Ä‘á»ƒ phĂ¢n tĂ­ch")
                    .extractedKeywords(Collections.emptyList())
                    .build();
        }

        if (!hasValidApiKey()) {
            log.info("Gemini API key is not configured for Vision Moderation. Allowing image by default.");
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("Há»‡ thá»‘ng kiá»ƒm duyá»‡t hĂ¬nh áº£nh hoáº¡t Ä‘á»™ng á»Ÿ cháº¿ Ä‘á»™ cÆ¡ báº£n (API key chÆ°a cáº¥u hĂ¬nh)")
                    .extractedKeywords(Collections.emptyList())
                    .isFallback(true)
                    .build();
        }

        Optional<Map<String, Object>> imagePartOpt = fetchImageInlinePart(imageUrl);
        if (imagePartOpt.isEmpty()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("KhĂ´ng thá»ƒ táº£i hĂ¬nh áº£nh tá»« URL Ä‘á»ƒ phĂ¢n tĂ­ch")
                    .extractedKeywords(Collections.emptyList())
                    .isFallback(true)
                    .build();
        }

        String prompt = """
                Báº¡n lĂ  má»™t chuyĂªn gia AI kiá»ƒm duyá»‡t hĂ¬nh áº£nh vĂ  ná»™i dung Ä‘a phÆ°Æ¡ng tiá»‡n máº¡ng xĂ£ há»™i.
                Nhiá»‡m vá»¥ cá»§a báº¡n lĂ  quan sĂ¡t trá»±c tiáº¿p bá»©c áº£nh Ä‘Æ°á»£c cung cáº¥p cĂ¹ng chĂº thĂ­ch bĂ i viáº¿t (náº¿u cĂ³) vĂ  Ä‘Ă¡nh giĂ¡ tĂ­nh an toĂ n.
                
                TiĂªu chuáº©n kiá»ƒm duyá»‡t vi pháº¡m:
                1. SEXUAL (18+ / khiĂªu dĂ¢m): áº¢nh khá»a thĂ¢n, lá»™ bá»™ pháº­n nháº¡y cáº£m, Ä‘á»“ lĂ³t khiĂªu dĂ¢m, hĂ nh vi tĂ¬nh dá»¥c.
                2. VIOLENCE (báº¡o lá»±c): VÅ© khĂ­ nguy hiá»ƒm, cáº£nh tÆ°á»£ng mĂ¡u me, hĂ nh vi ngÆ°á»£c Ä‘Ă£i, tai náº¡n kinh dá»‹.
                3. HATE_SPEECH (thĂ¹ ghĂ©t): Biá»ƒu tÆ°á»£ng phĂ¡t xĂ­t, phĂ¢n biá»‡t chá»§ng tá»™c, xĂºc pháº¡m tĂ´n giĂ¡o hoáº·c vÄƒn hĂ³a.
                4. SPAM_SCAM (lá»«a Ä‘áº£o): Banner cá» báº¡c, cĂ¡ Ä‘á»™ bĂ³ng Ä‘Ă¡, thĂ´ng tin lá»«a Ä‘áº£o tĂ i chĂ­nh.
                
                Äá»‹nh dáº¡ng tráº£ vá» Báº®T BUá»˜C lĂ  JSON thuáº§n tĂºy (khĂ´ng bá»c trong markdown ```json):
                {
                  "toxicityScore": 0.0,
                  "category": "NONE",
                  "severity": "LOW",
                  "suggestedAction": "ALLOW",
                  "reason": "Giáº£i thĂ­ch ngáº¯n gá»n 1 cĂ¢u báº±ng Tiáº¿ng Viá»‡t vá» ná»™i dung bá»©c áº£nh vĂ  lĂ½ do Ä‘Æ°a ra Ä‘Ă¡nh giĂ¡",
                  "extractedKeywords": []
                }
                """;

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt + "\n\nChĂº thĂ­ch áº£nh: " + (caption != null ? caption : "")));
        parts.add(imagePartOpt.get());

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", parts)
                        ),
                        "generationConfig", Map.of(
                                "temperature", 0.1,
                                "maxOutputTokens", 2048,
                                "responseMimeType", "application/json"
                        )
                );

                String requestJson = objectMapper.writeValueAsString(requestBodyMap);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode candidates = rootNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode candidate = candidates.get(0);
                        String rawJsonText = extractTextFromCandidate(candidate);

                        if (rawJsonText.startsWith("```json")) rawJsonText = rawJsonText.substring(7);
                        if (rawJsonText.startsWith("```")) rawJsonText = rawJsonText.substring(3);
                        if (rawJsonText.endsWith("```")) rawJsonText = rawJsonText.substring(0, rawJsonText.length() - 3);
                        rawJsonText = rawJsonText.trim();

                        int firstBrace = rawJsonText.indexOf('{');
                        int lastBrace = rawJsonText.lastIndexOf('}');
                        if (firstBrace >= 0 && lastBrace > firstBrace) {
                            rawJsonText = rawJsonText.substring(firstBrace, lastBrace + 1);
                        }

                        JsonNode parsed = objectMapper.readTree(rawJsonText);
                        double toxicityScore = parsed.path("toxicityScore").asDouble(0.0);
                        String category = parsed.path("category").asText("NONE");
                        String severity = parsed.path("severity").asText("LOW");
                        String suggestedAction = parsed.path("suggestedAction").asText("ALLOW");
                        String reason = parsed.path("reason").asText("HĂ¬nh áº£nh an toĂ n vĂ  phĂ¹ há»£p");
                        List<String> keywords = new ArrayList<>();
                        JsonNode kwNode = parsed.path("extractedKeywords");
                        if (kwNode.isArray()) {
                            for (JsonNode kn : kwNode) keywords.add(kn.asText());
                        }

                        return GeminiModerationResult.builder()
                                .isToxic(toxicityScore >= 0.5)
                                .toxicityScore(toxicityScore)
                                .category(category)
                                .severity(severity)
                                .suggestedAction(suggestedAction)
                                .reason(reason)
                                .extractedKeywords(keywords)
                                .isFallback(false)
                                .build();
                    }
                }
            } catch (Exception e) {
                log.warn("Gemini vision moderation error with model {}: {}", candidateModel, e.getMessage());
            }
        }

        return GeminiModerationResult.builder()
                .isToxic(false)
                .toxicityScore(0.0)
                .category("NONE")
                .severity("LOW")
                .suggestedAction("ALLOW")
                .reason("HĂ¬nh áº£nh an toĂ n (fallback)")
                .extractedKeywords(Collections.emptyList())
                .isFallback(true)
                .build();
    }

    /**
     * Danh sĂ¡ch cĂ¡c model embedding dá»± phĂ²ng theo thá»© tá»± Æ°u tiĂªn cá»§a Google Gemini
     */
    private static final List<String> EMBEDDING_CANDIDATE_MODELS = List.of(
            "text-embedding-004",
            "embedding-001",
            "gemini-embedding-001"
    );

    /**
     * Táº¡o vector embedding 768 chiá»u tá»« vÄƒn báº£n sá»­ dá»¥ng mĂ´ hĂ¬nh Gemini Embedding
     * vá»›i outputDimensionality = 768 (tiĂªu chuáº©n chuáº©n hoĂ¡ Transformer cho tĂ¬m kiáº¿m ngá»¯ nghÄ©a).
     */
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        if (!hasValidApiKey()) {
            log.warn("Gemini API key is not configured. Cannot generate embedding.");
            return Collections.emptyList();
        }

        for (String embModel : EMBEDDING_CANDIDATE_MODELS) {
            try {
                String endpoint = String.format("%s/%s:embedContent?key=%s", apiUrl, embModel, apiKey);

                Map<String, Object> requestBodyMap = Map.of(
                        "model", "models/" + embModel,
                        "content", Map.of(
                                "parts", List.of(Map.of("text", text.trim()))
                        ),
                        "outputDimensionality", 768
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
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode valuesNode = rootNode.path("embedding").path("values");
                    if (valuesNode.isArray() && !valuesNode.isEmpty()) {
                        List<Float> result = new ArrayList<>(valuesNode.size());
                        for (JsonNode val : valuesNode) {
                            result.add((float) val.asDouble());
                        }
                        return result;
                    }
                } else if (response.statusCode() == 404) {
                    log.debug("Gemini model {} not found, trying next candidate...", embModel);
                } else {
                    log.warn("Gemini embedContent ({}) returned status code {}: {}", embModel, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Failed to generate Gemini embedding with {}: {}", embModel, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Táº¡o vector embedding hĂ ng loáº¡t cho nhiá»u vÄƒn báº£n (tá»‘i Æ°u hĂ³a khi backfill hoáº·c nhiá»u bĂ i viáº¿t).
     */
    public List<List<Float>> generateBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        if (!hasValidApiKey()) {
            log.warn("Gemini API key is not configured. Cannot generate batch embeddings.");
            return Collections.emptyList();
        }

        for (String embModel : EMBEDDING_CANDIDATE_MODELS) {
            try {
                String endpoint = String.format("%s/%s:batchEmbedContents?key=%s", apiUrl, embModel, apiKey);

                List<Map<String, Object>> requests = new ArrayList<>();
                for (String text : texts) {
                    String safeText = (text != null && !text.isBlank()) ? text.trim() : " ";
                    requests.add(Map.of(
                            "model", "models/" + embModel,
                            "content", Map.of("parts", List.of(Map.of("text", safeText))),
                            "outputDimensionality", 768
                    ));
                }

                if (requests.isEmpty()) {
                    return Collections.emptyList();
                }

                Map<String, Object> requestBodyMap = Map.of("requests", requests);
                String requestJson = objectMapper.writeValueAsString(requestBodyMap);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(25))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode embeddingsNode = rootNode.path("embeddings");
                    if (embeddingsNode.isArray()) {
                        List<List<Float>> results = new ArrayList<>();
                        for (JsonNode item : embeddingsNode) {
                            JsonNode valuesNode = item.path("values");
                            List<Float> list = new ArrayList<>();
                            for (JsonNode val : valuesNode) {
                                list.add((float) val.asDouble());
                            }
                            results.add(list);
                        }
                        return results;
                    }
                } else if (response.statusCode() == 404) {
                    log.debug("Gemini batch model {} not found, trying next candidate...", embModel);
                } else {
                    log.warn("Gemini batchEmbedContents ({}) returned status code {}: {}", embModel, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Failed to generate batch Gemini embeddings with {}: {}", embModel, e.getMessage());
            }
        }
        return Collections.emptyList();
    }
}