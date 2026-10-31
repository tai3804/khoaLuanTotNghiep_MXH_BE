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
     * Phân tích ngữ cảnh nội dung bài viết/bình luận bằng Google Gemini Flash
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

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your-gemini-api-key")) {
            log.warn("Gemini API key is not configured or empty. Using Fast Rule-based fallback engine.");
            return createFallbackResult(content);
        }

        for (String candidateModel : getCandidateModels()) {
            try {
                String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, candidateModel, apiKey);

                String systemPrompt = """
                        Bạn là một AI kiểm duyệt nội dung mạng xã hội Tiếng Việt thông minh và công tâm.
                        Nhiệm vụ của bạn là phân tích văn bản bài viết hoặc bình luận và trả về kết quả JSON thuần túy (không bọc trong markdown hay ```json).
                        
                        Quy tắc nhận diện quan trọng (TRÁNH BẮT OAN):
                        1. TUYỆT ĐỐI KHÔNG đánh giá độc hại các thẻ gắn tên người dùng (ví dụ: @Điềm, @Điểm, @Duy, @Dung, @Huệ, @[Tên Bạn]...).
                        2. TUYỆT ĐỐI KHÔNG nhầm lẫn các từ ngữ ngữ pháp hoặc từ vựng lành mạnh thông dụng tiếng Việt (điểm số, điềm báo, địa điểm, giáo dục, du lịch, nguồn gốc, con ong, cái lon, các bạn...) với từ cấm/tục tĩu.
                        3. Chỉ xử lý vi phạm khi có hành vi chửi bới, lăng mạ, xúc phạm danh dự người khác, đe dọa bạo lực, nội dung 18+ đồi trụy hoặc lừa đảo cờ bạc rõ ràng.
                        
                        Các trường kết quả JSON:
                        - toxicityScore: Điểm độc hại từ 0.0 (hoàn toàn an toàn) đến 1.0 (cực kỳ độc hại/nguy hiểm). Nếu nội dung bình thường, hãy trả về 0.0.
                        - category: 1 trong các loại: NONE, PROFANITY (từ tục tĩu), HATE_SPEECH (thù ghét), HARASSMENT (quấy rối/xúc phạm), SEXUAL (18+/khiêu dâm), VIOLENCE (bạo lực/đe dọa), SPAM_SCAM (lừa đảo/cờ bạc).
                        - severity: 1 trong các mức: LOW, MEDIUM, HIGH, CRITICAL. (Nếu an toàn, để LOW).
                        - suggestedAction: 1 trong các hành động: ALLOW (cho phép), WARN_USER (cảnh báo tác giả), AUTO_HIDE (tự động ẩn bài), DELETE_POST (xóa bài viết). (Nếu an toàn, luôn là ALLOW).
                        - reason: Giải thích ngắn gọn lý do bằng tiếng Việt (1 câu, dùng từ 'Nội dung bài viết' hoặc 'Bài viết', ví dụ: 'Nội dung bài viết chứa từ ngữ thô tục, xúc phạm'). Tuyệt đối không dùng từ 'Bình luận' khi giải thích bài viết.
                        - extractedKeywords: Mảng các từ ngữ nhạy cảm thực sự có trong bài viết (chữ thường), để rỗng [] nếu bài viết an toàn.
                        
                        Ví dụ định dạng an toàn:
                        {"toxicityScore": 0.0, "category": "NONE", "severity": "LOW", "suggestedAction": "ALLOW", "reason": "Nội dung an toàn, gắn thẻ bạn bè bình thường", "extractedKeywords": []}
                        """;

                Map<String, Object> requestBodyMap = Map.of(
                        "contents", List.of(
                                Map.of("role", "user", "parts", List.of(
                                        Map.of("text", systemPrompt + "\n\nNội dung cần phân tích:\n" + content)
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

            // Kiểm tra safety block ở mức promptFeedback
            JsonNode promptFeedback = rootNode.path("promptFeedback");
            String blockReason = promptFeedback.path("blockReason").asText("");
            if (!blockReason.isBlank()) {
                return GeminiModerationResult.builder()
                        .isToxic(true)
                        .toxicityScore(0.95)
                        .category("SAFETY_BLOCK")
                        .severity("CRITICAL")
                        .suggestedAction("AUTO_HIDE")
                        .reason("Nội dung bị chặn bởi chính sách an toàn AI: " + blockReason)
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
                        .reason("Nội dung vi phạm chính sách an toàn (" + finishReason + ")")
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
        String reason = "Nội dung an toàn, phù hợp với tiêu chuẩn cộng đồng.";

        if (scan.isToxic()) {
            String keywordsStr = String.join(", ", scan.getMatchedKeywords());
            String category = scan.getPrimaryCategory() != null ? scan.getPrimaryCategory() : "PROFANITY";

            String categoryDesc = switch (category.toUpperCase()) {
                case "PROFANITY" -> "sử dụng từ ngữ dung tục hoặc thiếu chuẩn mực";
                case "HATE_SPEECH" -> "chứa ngôn từ kích động, phân biệt hoặc thù ghét";
                case "HARASSMENT" -> "có dấu hiệu công kích, quấy rối hoặc xúc phạm danh dự";
                case "VIOLENCE" -> "chứa nội dung bạo lực hoặc đe dọa an toàn cá nhân";
                case "SPAM_SCAM" -> "có dấu hiệu quảng cáo rác, lừa đảo hoặc cờ bạc";
                case "SEXUAL" -> "chứa nội dung 18+ nhạy cảm hoặc khiêu dâm";
                default -> "chứa từ ngữ vi phạm quy tắc ứng xử văn minh";
            };

            if ("CRITICAL".equalsIgnoreCase(scan.getHighestSeverity()) || scan.getToxicityScore() >= 0.85) {
                action = "DELETE_POST";
                reason = String.format("Vi phạm nghiêm trọng chuẩn mực cộng đồng do %s (từ khóa: %s). Hệ thống đã tự động gỡ bỏ để bảo vệ an toàn cho thành viên.", categoryDesc, keywordsStr);
            } else {
                action = "AUTO_HIDE";
                reason = String.format("Bài viết đã bị tạm ẩn do %s (từ khóa: %s). Bạn có thể chỉnh sửa lại bài viết để phù hợp với văn hóa mạng xã hội.", categoryDesc, keywordsStr);
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
     * Trò chuyện đa lượt có nhớ ngữ cảnh (Multi-turn Contextual Conversation)
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
                                Bạn là Trợ lý AI KLTN Social thông minh, thân thiện, đồng hành cùng sinh viên và giới trẻ.
                                Phong cách giao tiếp:
                                - Trả lời súc tích, tự nhiên, văn minh, có emoji phù hợp nhưng không lạm dụng.
                                - Đi thẳng vào trọng tâm câu hỏi của người dùng, TUYỆT ĐỐI không lặp lại câu chào khuôn mẫu ở mỗi lượt trả lời.
                                - Hỗ trợ chuyên sâu về: Lập trình (Spring Boot, React, Microservices...), Học tập, Làm đồ án KLTN, Sáng tạo bài viết, Giải tỏa áp lực.
                                - Sử dụng Markdown đẹp mắt (gạch đầu dòng, in đậm từ khóa quan trọng).
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
     * Tải ảnh từ URL chuyển thành inline_data Base64 cho Gemini Multimodal Vision (ngăn SSRF + giới hạn 10MB)
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
     * Tóm tắt cuộc trò chuyện Đa phương thức (Multimodal: Text + Hình ảnh) bằng Gemini
     */
    public MessageSummaryDto.SummarizeMessagesResponse summarizeMessagesWithGemini(
            MessageSummaryDto.SummarizeMessagesRequest request,
            MessageSummaryDto.SummarizeMessagesResponse fallback) {

        if (!hasValidApiKey() || request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return fallback;
        }

        List<MessageSummaryDto.MessageItem> messages = request.getMessages();
        String convName = request.getConversationName() != null ? request.getConversationName() : "Cuộc trò chuyện";
        boolean isGroup = Boolean.TRUE.equals(request.getIsGroup());

        StringBuilder chatTranscript = new StringBuilder();
        chatTranscript.append(String.format("Tên cuộc hội thoại: %s (%s)\n", convName, isGroup ? "Nhóm chat" : "Chat 1-1"));
        chatTranscript.append("Danh sách các tin nhắn gần nhất:\n");

        List<String> imageUrlsToFetch = new ArrayList<>();
        int imageCount = 0;

        for (MessageSummaryDto.MessageItem item : messages) {
            String sender = item.getSenderName() != null ? item.getSenderName() : "Thành viên";
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
                chatTranscript.append(String.format("[%s] %s: [ĐÃ GỬI 1 HÌNH ẢNH ĐÍNH KÈM]\n", time, sender));
            } else {
                chatTranscript.append(String.format("[%s] %s: %s\n", time, sender, text));
            }
        }

        List<Map<String, Object>> parts = new ArrayList<>();

        String systemInstruction = """
                Bạn là Trợ lý AI phân tích và tóm tắt cuộc trò chuyện mạng xã hội (hỗ trợ cả văn bản và hình ảnh đính kèm).
                Nhiệm vụ của bạn:
                1. Đọc kỹ dòng thời gian tin nhắn và quan sát trực tiếp các hình ảnh đính kèm (nếu có).
                2. Tóm tắt nội dung chính xác, ngắn gọn, súc tích (1-2 đoạn).
                3. Mô tả nội dung hình ảnh đính kèm (ảnh chụp bài học, slide, tài liệu, sơ đồ, ảnh đời thường...).
                4. Trích xuất danh sách các việc cần làm, deadline, lịch hẹn (actionItems).
                
                Định dạng trả về BẮT BUỘC là JSON thuần túy (không bọc trong markdown ```json):
                {
                  "summary": "Tóm tắt ngắn gọn các vấn đề chính đã thảo luận...",
                  "mediaDescription": "Mô tả chi tiết nội dung các ảnh đính kèm (hoặc 'Không có hình ảnh đính kèm' nếu không có)...",
                  "actionItems": ["Việc 1 cần làm (kèm người phụ trách / deadline nếu có)", "Việc 2..."]
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
     * Kiểm duyệt hình ảnh đa phương thức (Multimodal Vision Moderation) bằng Google Gemini Vision
     */
    public GeminiModerationResult evaluateImageWithVision(String imageUrl, String caption) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return GeminiModerationResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .category("NONE")
                    .severity("LOW")
                    .suggestedAction("ALLOW")
                    .reason("Không có hình ảnh để phân tích")
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
                    .reason("Hệ thống kiểm duyệt hình ảnh hoạt động ở chế độ cơ bản (API key chưa cấu hình)")
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
                    .reason("Không thể tải hình ảnh từ URL để phân tích")
                    .extractedKeywords(Collections.emptyList())
                    .isFallback(true)
                    .build();
        }

        String prompt = """
                Bạn là một chuyên gia AI kiểm duyệt hình ảnh và nội dung đa phương tiện mạng xã hội.
                Nhiệm vụ của bạn là quan sát trực tiếp bức ảnh được cung cấp cùng chú thích bài viết (nếu có) và đánh giá tính an toàn.
                
                Tiêu chuẩn kiểm duyệt vi phạm:
                1. SEXUAL (18+ / khiêu dâm): Ảnh khỏa thân, lộ bộ phận nhạy cảm, đồ lót khiêu dâm, hành vi tình dục.
                2. VIOLENCE (bạo lực): Vũ khí nguy hiểm, cảnh tượng máu me, hành vi ngược đãi, tai nạn kinh dị.
                3. HATE_SPEECH (thù ghét): Biểu tượng phát xít, phân biệt chủng tộc, xúc phạm tôn giáo hoặc văn hóa.
                4. SPAM_SCAM (lừa đảo): Banner cờ bạc, cá độ bóng đá, thông tin lừa đảo tài chính.
                
                Định dạng trả về BẮT BUỘC là JSON thuần túy (không bọc trong markdown ```json):
                {
                  "toxicityScore": 0.0,
                  "category": "NONE",
                  "severity": "LOW",
                  "suggestedAction": "ALLOW",
                  "reason": "Giải thích ngắn gọn 1 câu bằng Tiếng Việt về nội dung bức ảnh và lý do đưa ra đánh giá",
                  "extractedKeywords": []
                }
                """;

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt + "\n\nChú thích ảnh: " + (caption != null ? caption : "")));
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
                        String reason = parsed.path("reason").asText("Hình ảnh an toàn và phù hợp");
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
                .reason("Hình ảnh an toàn (fallback)")
                .extractedKeywords(Collections.emptyList())
                .isFallback(true)
                .build();
    }
}
