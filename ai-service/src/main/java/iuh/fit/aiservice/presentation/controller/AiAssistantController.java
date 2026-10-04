package iuh.fit.aiservice.presentation.controller;

import iuh.fit.aiservice.application.engine.SmartAssistantEngine;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final GeminiApiClient geminiApiClient;
    private final SmartAssistantEngine smartAssistantEngine;

    @PostMapping("/suggest-caption")
    public ResponseEntity<ApiResponse<CaptionSuggestionResponse>> suggestCaption(@RequestBody CaptionRequest request) {
        String topic = request.getTopic() != null ? request.getTopic().trim() : "";
        String tone = request.getTone() != null ? request.getTone() : "tự nhiên, thân thiện";

        List<String> defaultCaptions = smartAssistantEngine.suggestCaptions(topic, tone);

        if (topic.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    new CaptionSuggestionResponse(defaultCaptions),
                    "Gợi ý caption mặc định thành công"
            ));
        }

        String prompt = String.format("""
                Bạn là một trợ lý viết nội dung mạng xã hội Tiếng Việt thông minh, chuẩn mực.
                Hãy gợi ý đúng 3 dòng caption bài viết ngắn gọn, hấp dẫn, giọng điệu %s dựa trên chủ đề hoặc ý tưởng sau:
                "%s"
                
                Yêu cầu:
                - Mỗi gợi ý nằm trên một dòng riêng biệt, không đánh số thứ tự 1 2 3, không dùng ký tự gạch đầu dòng.
                - Ngôn từ văn minh, đúng chính tả, không dùng từ ngữ tiêu cực hay vi phạm chuẩn mực.
                """, tone, topic);

        String fallback = String.join("\n", defaultCaptions);
        String rawResult = geminiApiClient.generateText(prompt, fallback);

        List<String> suggestions = Arrays.stream(rawResult.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[0-9]+[.\\-\\)]\\s*", "").replaceFirst("^[\\-*•]\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();

        if (suggestions.isEmpty()) {
            suggestions = defaultCaptions;
        }

        return ResponseEntity.ok(ApiResponse.success(new CaptionSuggestionResponse(suggestions), "Tạo gợi ý nội dung thành công"));
    }

    @PostMapping("/enhance")
    public ResponseEntity<ApiResponse<EnhanceTextResponse>> enhanceText(@RequestBody EnhanceTextRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        String style = request.getStyle() != null ? request.getStyle() : "lịch sự, rõ ràng";

        if (content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(new EnhanceTextResponse(content, content), "Nội dung rỗng"));
        }

        String prompt = String.format("""
                Bạn là chuyên gia biên tập nội dung tiếng Việt cho mạng xã hội.
                Nhiệm vụ của bạn: Hãy viết lại, trau chuốt và nâng cấp đoạn văn bản sau theo phong cách %s:
                - Chuẩn hóa chính tả, dấu câu, sửa lỗi ngữ pháp và viết tắt / teencode nếu có.
                - Nâng cấp câu từ mượt mà, hấp dẫn và phù hợp với phong cách %s.
                - Giữ nguyên vẹn thông điệp và ý chính ban đầu của tác giả.
                - BẮT BUỘC: Chỉ trả về duy nhất văn bản sau khi đã chỉnh sửa hoàn chỉnh. Không thêm lời mở đầu (ví dụ: 'Đây là bản sửa...', 'Bản nâng cấp:'), không bọc trong dấu ngoặc kép hay markdown.
                
                Văn bản gốc:
                %s
                """, style, style, content);

        String fallback = smartAssistantEngine.enhanceText(content, style);
        String enhanced = geminiApiClient.generateText(prompt, fallback);
        if (enhanced != null) {
            enhanced = enhanced.trim();
            if (enhanced.startsWith("\"") && enhanced.endsWith("\"") && enhanced.length() > 1) {
                enhanced = enhanced.substring(1, enhanced.length() - 1).trim();
            }
        } else {
            enhanced = fallback;
        }

        return ResponseEntity.ok(ApiResponse.success(
                new EnhanceTextResponse(content, enhanced),
                "Nâng cấp nội dung bài viết thành công"
        ));
    }

    @PostMapping("/suggest-hashtags")
    public ResponseEntity<ApiResponse<HashtagSuggestionResponse>> suggestHashtags(@RequestBody HashtagRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        int count = request.getCount() > 0 ? Math.min(request.getCount(), 10) : 5;

        List<String> defaultHashtags = smartAssistantEngine.suggestHashtags(content, count);

        if (content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    new HashtagSuggestionResponse(defaultHashtags),
                    "Gợi ý hashtag mặc định thành công"
            ));
        }

        String prompt = String.format("""
                Bạn là chuyên gia tối ưu nội dung mạng xã hội.
                Hãy gợi ý đúng %d hashtag phổ biến, phù hợp và viral nhất dựa trên nội dung bài viết sau:
                "%s"
                
                Yêu cầu:
                - Mỗi hashtag phải bắt đầu bằng dấu #, viết liền không dấu hoặc PascalCase không dấu cách (ví dụ: #CongNghe #LapTrinh #SinhVien #KhoaLuan).
                - Các hashtag cách nhau bằng dấu cách hoặc mỗi hashtag trên một dòng.
                - Chỉ trả về danh sách hashtag, không thêm bất kỳ văn bản giải thích nào khác.
                """, count, content);

        String fallback = String.join(" ", defaultHashtags);
        String rawResult = geminiApiClient.generateText(prompt, fallback);

        List<String> hashtags = Arrays.stream(rawResult.split("[\\s,\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.startsWith("#") ? s : "#" + s)
                .distinct()
                .limit(count)
                .toList();

        if (hashtags.isEmpty()) {
            hashtags = defaultHashtags;
        }

        return ResponseEntity.ok(ApiResponse.success(new HashtagSuggestionResponse(hashtags), "Gợi ý hashtag thành công"));
    }

    @PostMapping("/summarize")
    public ResponseEntity<ApiResponse<SummarizeResponse>> summarizeText(@RequestBody SummarizeRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(new SummarizeResponse(content, content), "Nội dung rỗng"));
        }

        String fallback = smartAssistantEngine.summarizeText(content);
        String prompt = String.format("""
                Bạn là một trợ lý AI thông minh tóm tắt nội dung bài viết mạng xã hội.
                Nhiệm vụ: Hãy tóm tắt văn bản sau thành một đoạn ngắn gọn (1-2 câu súc tích) hoặc 2-3 gạch đầu dòng nêu bật ý chính quan trọng nhất:
                - Ngắn gọn, dễ hiểu, ngôn từ tự nhiên bằng Tiếng Việt.
                - BẮT BUỘC: Chỉ trả về nội dung tóm tắt hoàn chỉnh, không thêm lời chào, không thêm 'Tóm tắt bài viết:'.
                
                Nội dung bài viết:
                %s
                """, content);

        String summary = geminiApiClient.generateText(prompt, fallback);
        if (summary != null) {
            summary = summary.trim();
        } else {
            summary = fallback;
        }

        return ResponseEntity.ok(ApiResponse.success(new SummarizeResponse(content, summary), "Tóm tắt bài viết thành công"));
    }

    @PostMapping("/translate")
    public ResponseEntity<ApiResponse<TranslateResponse>> translateText(@RequestBody TranslateRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        String targetLang = request.getTargetLanguage() != null && !request.getTargetLanguage().isBlank() ? request.getTargetLanguage().trim() : "vi";

        if (content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(new TranslateResponse(content, content, targetLang), "Nội dung rỗng"));
        }

        String targetLangName = switch (targetLang.toLowerCase()) {
            case "en" -> "Tiếng Anh (English)";
            case "ja" -> "Tiếng Nhật (Japanese)";
            case "ko" -> "Tiếng Hàn (Korean)";
            case "zh" -> "Tiếng Trung (Chinese)";
            case "fr" -> "Tiếng Pháp (French)";
            default -> "Tiếng Việt (Vietnamese)";
        };

        String prompt = String.format("""
                Bạn là một chuyên gia dịch thuật chuyên nghiệp, giàu sắc thái tự nhiên cho mạng xã hội.
                Nhiệm vụ: Hãy dịch văn bản sau sang ngôn ngữ %s:
                - Bản dịch tự nhiên, trôi chảy, giữ nguyên ngữ cảnh và cảm xúc ban đầu.
                - BẮT BUỘC: Chỉ trả về duy nhất bản dịch, không giải thích thêm, không có lời mở đầu hay kết thúc.
                
                Văn bản gốc:
                %s
                """, targetLangName, content);

        String translated = geminiApiClient.generateText(prompt, content);
        if (translated != null) {
            translated = translated.trim();
        } else {
            translated = content;
        }

        return ResponseEntity.ok(ApiResponse.success(new TranslateResponse(content, translated, targetLang), "Dịch nội dung thành công"));
    }

    @PostMapping("/suggest-replies")
    public ResponseEntity<ApiResponse<SmartRepliesResponse>> suggestReplies(@RequestBody SmartRepliesRequest request) {
        String postContent = request.getPostContent() != null ? request.getPostContent().trim() : "";
        String commentContent = request.getCommentContent() != null ? request.getCommentContent().trim() : "";

        List<String> defaultReplies = smartAssistantEngine.suggestReplies(postContent, commentContent);
        String context = !commentContent.isBlank() ? "Bình luận: \"" + commentContent + "\" (trong bài viết: \"" + postContent + "\")" : "Bài viết: \"" + postContent + "\"";

        String prompt = String.format("""
                Bạn là trợ lý gợi ý tương tác mạng xã hội.
                Dựa vào ngữ cảnh sau: %s
                Hãy gợi ý đúng 3 câu phản hồi/bình luận ngắn gọn, tích cực, lịch thiệp và tự nhiên nhất (có thể kèm emoji phù hợp).
                
                Yêu cầu:
                - Mỗi gợi ý nằm trên một dòng riêng biệt, không đánh số thứ tự 1 2 3, không dùng ký tự gạch đầu dòng.
                - Độ dài mỗi câu từ 3 - 15 từ.
                - Chỉ trả về 3 dòng gợi ý.
                """, context);

        String fallback = String.join("\n", defaultReplies);
        String rawResult = geminiApiClient.generateText(prompt, fallback);

        List<String> replies = Arrays.stream(rawResult.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[0-9]+[.\\-\\)]\\s*", "").replaceFirst("^[\\-*•]\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();

        if (replies.isEmpty()) {
            replies = defaultReplies;
        }

        return ResponseEntity.ok(ApiResponse.success(new SmartRepliesResponse(replies), "Gợi ý câu trả lời nhanh thành công"));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chatWithAssistant(@RequestBody ChatRequest request) {
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
        if (message.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(new ChatResponse("Xin chào! Tôi có thể giúp gì cho bạn hôm nay?"), "Tin nhắn rỗng"));
        }

        String fallbackReply = smartAssistantEngine.generateChatReply(message);

        String prompt = String.format("""
                Bạn là Trợ lý AI KLTN Social thông minh, nhiệt tình, thân thiện của mạng xã hội sinh viên/giới trẻ.
                Nhiệm vụ của bạn là hỗ trợ người dùng:
                - Gợi ý ý tưởng đăng bài, viết caption, hashtag, sáng tạo nội dung.
                - Trả lời các câu hỏi học tập, lập trình, công nghệ, cuộc sống sinh viên.
                - Trao đổi văn minh, thân thiện, súc tích, định dạng markdown đẹp mắt (in đậm, bullet points khi cần).
                
                Người dùng hỏi:
                "%s"
                """, message);

        String reply = geminiApiClient.generateText(prompt, fallbackReply);

        return ResponseEntity.ok(ApiResponse.success(new ChatResponse(reply != null && !reply.isBlank() ? reply.trim() : fallbackReply), "Trả lời thành công"));
    }

    @PostMapping("/suggest-bio")
    public ResponseEntity<ApiResponse<BioSuggestionResponse>> suggestBio(@RequestBody BioRequest request) {
        String name = request.getName() != null ? request.getName().trim() : "";
        String major = request.getMajor() != null ? request.getMajor().trim() : "";
        String interests = request.getInterests() != null ? request.getInterests().trim() : "";
        String tone = request.getTone() != null ? request.getTone().trim() : "năng động, trẻ trung";

        List<String> defaultBios = smartAssistantEngine.suggestBios(name, major, interests, tone);

        String prompt = String.format("""
                Bạn là một chuyên gia sáng tạo nội dung tiểu sử cá nhân (Profile Bio) cho mạng xã hội sinh viên.
                Dựa trên thông tin người dùng:
                - Tên: %s
                - Chuyên ngành/Công việc: %s
                - Sở thích/Đam mê: %s
                - Phong cách/Giọng điệu: %s
                
                Hãy gợi ý đúng 3 câu tiểu sử cá nhân ngắn gọn (mỗi câu không quá 100 ký tự), súc tích, ấn tượng, có đính kèm 1-2 emoji phù hợp.
                
                Yêu cầu:
                - Mỗi gợi ý nằm trên một dòng riêng biệt, không đánh số thứ tự 1 2 3, không dùng ký tự gạch đầu dòng.
                - BẮT BUỘC: Chỉ trả về 3 dòng gợi ý, không giải thích hay mở đầu.
                """, name, major, interests, tone);

        String fallback = String.join("\n", defaultBios);
        String rawResult = geminiApiClient.generateText(prompt, fallback);

        List<String> suggestions = Arrays.stream(rawResult.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[0-9]+[.\\-\\)]\\s*", "").replaceFirst("^[\\-*•]\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();

        if (suggestions.isEmpty()) {
            suggestions = defaultBios;
        }

        return ResponseEntity.ok(ApiResponse.success(new BioSuggestionResponse(suggestions), "Gợi ý tiểu sử cá nhân thành công"));
    }

    @PostMapping("/analyze-post")
    public ResponseEntity<ApiResponse<PostAnalysisResponse>> analyzePost(@RequestBody PostAnalysisRequest request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        var fallback = smartAssistantEngine.analyzePost(content);

        if (content.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    new PostAnalysisResponse(fallback.getSentiment(), fallback.getEngagementScore(), fallback.getVibe(), fallback.getSuggestions()),
                    "Nội dung bài viết rỗng"
            ));
        }

        String prompt = String.format("""
                Bạn là chuyên gia phân tích nội dung mạng xã hội và tối ưu tương tác (Engagement Growth).
                Hãy phân tích bài viết sau và trả về đúng định dạng JSON thuần túy (không bọc trong markdown ```json):
                {
                  "sentiment": "POSITIVE",
                  "engagementScore": 85,
                  "vibe": "Tích cực & Truyền cảm hứng ✨",
                  "suggestions": ["Thêm câu hỏi kích thích bình luận ở cuối bài.", "Gắn thêm hashtag #KLTN."]
                }
                
                Nội dung bài viết:
                "%s"
                """, content);

        String rawResult = geminiApiClient.generateText(prompt, null);
        if (rawResult != null && !rawResult.isBlank()) {
            try {
                String cleanJson = rawResult.trim();
                if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
                if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3);
                if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                cleanJson = cleanJson.trim();

                com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanJson);
                String sentiment = rootNode.has("sentiment") ? rootNode.get("sentiment").asText() : fallback.getSentiment();
                int score = rootNode.has("engagementScore") ? rootNode.get("engagementScore").asInt() : fallback.getEngagementScore();
                String vibe = rootNode.has("vibe") ? rootNode.get("vibe").asText() : fallback.getVibe();
                List<String> suggestions = new ArrayList<>();
                if (rootNode.has("suggestions") && rootNode.get("suggestions").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode.get("suggestions")) {
                        suggestions.add(item.asText());
                    }
                }
                if (suggestions.isEmpty()) suggestions = fallback.getSuggestions();

                return ResponseEntity.ok(ApiResponse.success(
                        new PostAnalysisResponse(sentiment, score, vibe, suggestions),
                        "Phân tích bài viết bằng AI thành công"
                ));
            } catch (Exception e) {
                log.warn("Could not parse AI analyze-post response, using fallback: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                new PostAnalysisResponse(fallback.getSentiment(), fallback.getEngagementScore(), fallback.getVibe(), fallback.getSuggestions()),
                "Phân tích bài viết thành công"
        ));
    }

    @Data
    public static class BioRequest {
        private String name;
        private String major;
        private String interests;
        private String tone;
    }

    @Data
    public static class BioSuggestionResponse {
        private final List<String> suggestions;
    }

    @Data
    public static class PostAnalysisRequest {
        private String content;
    }

    @Data
    public static class PostAnalysisResponse {
        private final String sentiment;
        private final int engagementScore;
        private final String vibe;
        private final List<String> suggestions;
    }

    @Data
    public static class CaptionRequest {
        private String topic;
        private String tone;
    }

    @Data
    public static class CaptionSuggestionResponse {
        private final List<String> suggestions;
    }

    @Data
    public static class EnhanceTextRequest {
        private String content;
        private String style;
    }

    @Data
    public static class EnhanceTextResponse {
        private final String original;
        private final String enhanced;
    }

    @Data
    public static class HashtagRequest {
        private String content;
        private int count;
    }

    @Data
    public static class HashtagSuggestionResponse {
        private final List<String> hashtags;
    }

    @Data
    public static class SummarizeRequest {
        private String content;
        private int maxSentences;
    }

    @Data
    public static class SummarizeResponse {
        private final String original;
        private final String summary;
    }

    @Data
    public static class TranslateRequest {
        private String content;
        private String targetLanguage;
    }

    @Data
    public static class TranslateResponse {
        private final String original;
        private final String translated;
        private final String targetLanguage;
    }

    @Data
    public static class SmartRepliesRequest {
        private String postContent;
        private String commentContent;
    }

    @Data
    public static class SmartRepliesResponse {
        private final List<String> replies;
    }

    @Data
    public static class ChatRequest {
        private String message;
    }

    @Data
    public static class ChatResponse {
        private final String reply;
    }
}
