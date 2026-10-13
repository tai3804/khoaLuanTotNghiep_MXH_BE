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

            // 2. Chỉ bài an toàn và đủ dài mới được tóm tắt (chạy ngoài transaction kiểm duyệt vì gọi Gemini khá lâu)
            if (safe) {
                postSummaryService.summarizeAndPublish(event.getPostId(), event.getContent());
            }
        } catch (Exception e) {
            log.error("Failed to process new post {} in ai-service: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
