package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.aiservice.application.service.AiModerationService;
import iuh.fit.aiservice.application.service.PostSummaryService;
import iuh.fit.commonframework.event.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedEventListener {

    private final AiModerationService aiModerationService;
    private final PostSummaryService postSummaryService;

    @KafkaListener(topics = "post.created", groupId = "ai-service-post-group")
    public void handlePostCreated(PostCreatedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }

        log.info("Received PostCreatedEvent in ai-service for postId: {}, authorId: {}", event.getPostId(), event.getAuthorId());
        try {
            // 1. Kiểm duyệt: bài nghi vấn bị tạm ẩn và gắn cờ cho kiểm duyệt viên
            boolean safe = aiModerationService.evaluateNewPost(event.getPostId(), event.getAuthorId(), event.getContent());

            // 2. Chỉ bài an toàn mới được tóm tắt. Vector tìm kiếm do post-service tự tạo khi đăng bài (PostPublicationHelper);
            // trước đây ở đây còn tạo thêm một vector rồi gọi ngược post-service, nhưng lời gọi đó chạy trong luồng Kafka
            // không có JWT nên luôn bị từ chối, tức là chỉ tốn một lần gọi Gemini cho mỗi bài mới mà không lưu được gì.
            if (safe) {
                postSummaryService.summarizeAndPublish(event.getPostId(), event.getContent());
            }
        } catch (Exception e) {
            log.error("Failed to process new post {} in ai-service: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
