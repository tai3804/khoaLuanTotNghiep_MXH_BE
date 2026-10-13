package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.infrastructure.event.PostSummaryProducer;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Tóm tắt bài viết bằng Gemini. Bài viết chỉ được tóm tắt tự động khi dài hơn {@code ai.summary.min-length}
 * ký tự (mặc định 1000); bài ngắn không cần tóm tắt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSummaryService {

    private final GeminiApiClient geminiApiClient;
    private final PostSummaryProducer postSummaryProducer;

    @Value("${ai.summary.enabled:true}")
    private boolean summaryEnabled;

    @Value("${ai.summary.min-length:1000}")
    private int minLength;

    public boolean isLongEnough(String content) {
        return content != null && content.trim().length() > minLength;
    }

    /**
     * Tóm tắt nội dung (dùng cho REST /summarize). Trả về fallbackText nếu Gemini không khả dụng.
     */
    public String generate(String content, String fallbackText) {
        String summary = geminiApiClient.generateText(buildPrompt(content), fallbackText);
        return summary != null ? summary.trim() : fallbackText;
    }

    /**
     * Tóm tắt bài viết dài sau khi đã qua kiểm duyệt và gửi kết quả cho post-service qua Kafka.
     * Không dùng fallback cắt câu: nếu Gemini không khả dụng thì bỏ qua, không lưu bản tóm tắt kém chất lượng.
     */
    public void summarizeAndPublish(UUID postId, String content) {
        if (!summaryEnabled || postId == null) {
            return;
        }
        if (!isLongEnough(content)) {
            log.debug("Post {} is not longer than {} chars. Skipping auto-summary.", postId, minLength);
            return;
        }
        if (!geminiApiClient.hasValidApiKey()) {
            log.info("Gemini API key is not configured. Skipping auto-summary for post {}.", postId);
            return;
        }

        String text = content.trim();
        try {
            String summary = geminiApiClient.generateText(buildPrompt(text), null);
            if (summary == null || summary.isBlank()) {
                log.warn("Gemini returned no summary for post {}. Nothing stored.", postId);
                return;
            }
            postSummaryProducer.publishPostSummarized(postId, summary.trim(), text.length());
        } catch (Exception e) {
            log.error("Auto-summary failed for post {}: {}", postId, e.getMessage(), e);
        }
    }

    private String buildPrompt(String content) {
        return String.format("""
                Bạn là một trợ lý AI thông minh tóm tắt nội dung bài viết mạng xã hội.
                Nhiệm vụ: Hãy tóm tắt văn bản sau thành một đoạn ngắn gọn (1-2 câu súc tích) hoặc 2-3 gạch đầu dòng nêu bật ý chính quan trọng nhất:
                - Ngắn gọn, dễ hiểu, ngôn từ tự nhiên bằng Tiếng Việt.
                - BẮT BUỘC: Chỉ trả về nội dung tóm tắt hoàn chỉnh, không thêm lời chào, không thêm 'Tóm tắt bài viết:'.

                Nội dung bài viết:
                %s
                """, content);
    }
}
