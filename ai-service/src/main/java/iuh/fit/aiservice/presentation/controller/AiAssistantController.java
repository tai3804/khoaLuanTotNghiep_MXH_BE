package iuh.fit.aiservice.presentation.controller;

import iuh.fit.aiservice.application.dto.MessageSummaryDto;
import iuh.fit.aiservice.application.exception.AiServiceErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.aiservice.application.service.PostSummaryService;
import iuh.fit.aiservice.application.service.RagAssistantService;
import iuh.fit.aiservice.application.service.rag.RagContextBuilder;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private final PostSummaryService postSummaryService;
    private final RagAssistantService ragAssistantService;

    // Các tính năng AI trên giao diện KHÔNG có kết quả mặc định: Gemini không trả lời được thì báo lỗi 400
    // (AI_MODEL_UNAVAILABLE) để giao diện hiển thị rõ, thay vì trả về nội dung soạn sẵn trông như do AI tạo.

    /** Văn bản do Gemini trả về; null hoặc rỗng nghĩa là không gọi được mô hình. */
    private static String requireAiText(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }
        return text.trim();
    }

    /** Tách kết quả nhiều dòng của Gemini thành danh sách, bỏ số thứ tự và gạch đầu dòng. */
    private static List<String> requireAiLines(String raw, int limit) {
        List<String> lines = Arrays.stream(requireAiText(raw).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^[0-9]+[.\\-\\)]\\s*", "").replaceFirst("^[\\-*•]\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .limit(limit)
                .toList();
        if (lines.isEmpty()) {
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }
        return lines;
    }

    private static String requireInput(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(AiServiceErrorCode.AI_EMPTY_INPUT);
        }
        return value.trim();
    }

    @PostMapping("/suggest-caption")
    public ResponseEntity<ApiResponse<CaptionSuggestionResponse>> suggestCaption(@RequestBody CaptionRequest request) {
        String topic = requireInput(request.getTopic());
        String tone = request.getTone() != null ? request.getTone() : "tự nhiên, thân thiện";

        String prompt = String.format("""
                Bạn là một trợ lý viết nội dung mạng xã hội Tiếng Việt thông minh, chuẩn mực.
                Hãy gợi ý đúng 3 dòng caption bài viết ngắn gọn, hấp dẫn, giọng điệu %s dựa trên chủ đề hoặc ý tưởng sau:
                "%s"
                
                Yêu cầu:
                - Mỗi gợi ý nằm trên một dòng riêng biệt, không đánh số thứ tự 1 2 3, không dùng ký tự gạch đầu dòng.
                - Ngôn từ văn minh, đúng chính tả, không dùng từ ngữ tiêu cực hay vi phạm chuẩn mực.
                """, tone, topic);

        List<String> suggestions = requireAiLines(geminiApiClient.generateText(prompt, null), 3);

        return ResponseEntity.ok(ApiResponse.success(new CaptionSuggestionResponse(suggestions), "Tạo gợi ý nội dung thành công"));
    }

    @PostMapping("/enhance")
    public ResponseEntity<ApiResponse<EnhanceTextResponse>> enhanceText(@RequestBody EnhanceTextRequest request) {
        String content = requireInput(request.getContent());
        String style = request.getStyle() != null ? request.getStyle() : "lịch sự, rõ ràng";

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

        String enhanced = requireAiText(geminiApiClient.generateText(prompt, null));
        if (enhanced.startsWith("\"") && enhanced.endsWith("\"") && enhanced.length() > 1) {
            enhanced = enhanced.substring(1, enhanced.length() - 1).trim();
        }

        return ResponseEntity.ok(ApiResponse.success(
                new EnhanceTextResponse(content, enhanced),
                "Nâng cấp nội dung bài viết thành công"
        ));
    }

    @PostMapping("/suggest-hashtags")
    public ResponseEntity<ApiResponse<HashtagSuggestionResponse>> suggestHashtags(@RequestBody HashtagRequest request) {
        String content = requireInput(request.getContent());
        int count = request.getCount() > 0 ? Math.min(request.getCount(), 10) : 5;

        String prompt = String.format("""
                Bạn là chuyên gia tối ưu nội dung mạng xã hội.
                Hãy gợi ý đúng %d hashtag phổ biến, phù hợp và viral nhất dựa trên nội dung bài viết sau:
                "%s"
                
                Yêu cầu:
                - Mỗi hashtag phải bắt đầu bằng dấu #, viết liền không dấu hoặc PascalCase không dấu cách (ví dụ: #CongNghe #LapTrinh #SinhVien #KhoaLuan).
                - Các hashtag cách nhau bằng dấu cách hoặc mỗi hashtag trên một dòng.
                - Chỉ trả về danh sách hashtag, không thêm bất kỳ văn bản giải thích nào khác.
                """, count, content);

        String rawResult = requireAiText(geminiApiClient.generateText(prompt, null));

        List<String> hashtags = Arrays.stream(rawResult.split("[\\s,\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.startsWith("#") ? s : "#" + s)
                .distinct()
                .limit(count)
                .toList();

        if (hashtags.isEmpty()) {
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }

        return ResponseEntity.ok(ApiResponse.success(new HashtagSuggestionResponse(hashtags), "Gợi ý hashtag thành công"));
    }

    @PostMapping("/summarize")
    public ResponseEntity<ApiResponse<SummarizeResponse>> summarizeText(@RequestBody SummarizeRequest request) {
        String content = requireInput(request.getContent());

        String summary = requireAiText(postSummaryService.generate(content, null));

        return ResponseEntity.ok(ApiResponse.success(new SummarizeResponse(content, summary), "Tóm tắt bài viết thành công"));
    }

    @PostMapping("/translate")
    public ResponseEntity<ApiResponse<TranslateResponse>> translateText(@RequestBody TranslateRequest request) {
        String content = requireInput(request.getContent());
        String targetLang = request.getTargetLanguage() != null && !request.getTargetLanguage().isBlank() ? request.getTargetLanguage().trim() : "vi";

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

        String translated = requireAiText(geminiApiClient.generateText(prompt, null));

        return ResponseEntity.ok(ApiResponse.success(new TranslateResponse(content, translated, targetLang), "Dịch nội dung thành công"));
    }

    @PostMapping("/suggest-replies")
    public ResponseEntity<ApiResponse<SmartRepliesResponse>> suggestReplies(@RequestBody SmartRepliesRequest request) {
        String postContent = request.getPostContent() != null ? request.getPostContent().trim() : "";
        String commentContent = request.getCommentContent() != null ? request.getCommentContent().trim() : "";
        requireInput(postContent + commentContent);

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

        List<String> replies = requireAiLines(geminiApiClient.generateText(prompt, null), 3);

        return ResponseEntity.ok(ApiResponse.success(new SmartRepliesResponse(replies), "Gợi ý câu trả lời nhanh thành công"));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chatWithAssistant(@RequestBody ChatRequest request) {
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
        if (message.isBlank()) {
            throw new BusinessException(AiServiceErrorCode.AI_ASSISTANT_EMPTY_MESSAGE);
        }

        // RAG: truy xuất bài viết liên quan (theo quyền xem của người hỏi) rồi để mô hình sinh câu trả lời dựa trên tư liệu đó.
        // Không có câu trả lời dự phòng: nếu Gemini không trả lời được thì báo lỗi 400 rõ ràng cho giao diện.
        RagContextBuilder.Augmented rag = ragAssistantService.prepare(message);
        String reply = geminiApiClient.chatWithHistory(rag.prompt(), request.getHistory(), null);
        if (reply == null || reply.isBlank()) {
            log.warn("Trợ lý AI không nhận được câu trả lời từ Gemini cho tin nhắn dài {} ký tự", message.length());
            throw new BusinessException(AiServiceErrorCode.AI_ASSISTANT_UNAVAILABLE);
        }
        List<ChatSource> sources = rag.sources().stream()
                .map(p -> new ChatSource(p.postId().toString(), p.text().length() > 160 ? p.text().substring(0, 160).trim() + "..." : p.text()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(new ChatResponse(reply.trim(), sources), "Trả lời thành công"));
    }

    @PostMapping("/summarize-messages")
    public ResponseEntity<ApiResponse<MessageSummaryDto.SummarizeMessagesResponse>> summarizeMessages(
            @RequestBody MessageSummaryDto.SummarizeMessagesRequest request) {

        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BusinessException(AiServiceErrorCode.AI_EMPTY_INPUT);
        }

        var result = geminiApiClient.summarizeMessagesWithGemini(request, null);
        if (result == null || result.getSummary() == null || result.getSummary().isBlank()) {
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }

        return ResponseEntity.ok(ApiResponse.success(result, "Tóm tắt cuộc trò chuyện thành công"));
    }

    @PostMapping("/suggest-bio")
    public ResponseEntity<ApiResponse<BioSuggestionResponse>> suggestBio(@RequestBody BioRequest request) {
        String name = request.getName() != null ? request.getName().trim() : "";
        String major = request.getMajor() != null ? request.getMajor().trim() : "";
        String interests = request.getInterests() != null ? request.getInterests().trim() : "";
        String tone = request.getTone() != null ? request.getTone().trim() : "năng động, trẻ trung";

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

        List<String> suggestions = requireAiLines(geminiApiClient.generateText(prompt, null), 3);

        return ResponseEntity.ok(ApiResponse.success(new BioSuggestionResponse(suggestions), "Gợi ý tiểu sử cá nhân thành công"));
    }

    @PostMapping("/analyze-post")
    public ResponseEntity<ApiResponse<PostAnalysisResponse>> analyzePost(@RequestBody PostAnalysisRequest request) {
        String content = requireInput(request.getContent());

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

        String rawResult = requireAiText(geminiApiClient.generateText(prompt, null));
        com.fasterxml.jackson.databind.JsonNode rootNode;
        try {
            String cleanJson = rawResult;
            if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
            if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3);
            if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanJson.trim());
        } catch (Exception e) {
            log.warn("Không đọc được kết quả analyze-post của Gemini: {}", e.getMessage());
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }
        // Thiếu trường bắt buộc nghĩa là mô hình không trả về đúng định dạng: báo lỗi, không tự điền giá trị mặc định
        if (!rootNode.hasNonNull("sentiment") || !rootNode.hasNonNull("engagementScore") || !rootNode.hasNonNull("vibe")) {
            throw new BusinessException(AiServiceErrorCode.AI_MODEL_UNAVAILABLE);
        }
        List<String> suggestions = new ArrayList<>();
        if (rootNode.has("suggestions") && rootNode.get("suggestions").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : rootNode.get("suggestions")) {
                suggestions.add(item.asText());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                new PostAnalysisResponse(rootNode.get("sentiment").asText(), rootNode.get("engagementScore").asInt(),
                        rootNode.get("vibe").asText(), suggestions),
                "Phân tích bài viết bằng AI thành công"
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageItem {
        private String sender;
        private String text;
    }

    @Data
    public static class ChatRequest {
        private String message;
        private List<ChatMessageItem> history;
    }

    @Data
    public static class ChatResponse {
        private final String reply;
        /** Các bài viết được dùng làm tư liệu (RAG), theo đúng số thứ tự [1], [2], ... trong câu trả lời. Rỗng nếu không dùng. */
        private final List<ChatSource> sources;

        public ChatResponse(String reply) {
            this(reply, List.of());
        }

        public ChatResponse(String reply, List<ChatSource> sources) {
            this.reply = reply;
            this.sources = sources == null ? List.of() : sources;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ChatSource {
        private String postId;
        private String snippet;
    }
}
