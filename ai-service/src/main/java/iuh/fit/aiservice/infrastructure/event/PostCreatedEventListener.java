package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.aiservice.application.service.AiModerationService;
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

    @KafkaListener(topics = "post.created", groupId = "ai-service-post-group")
    public void handlePostCreated(PostCreatedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }

        log.info("Received PostCreatedEvent in ai-service for postId: {}, authorId: {}", event.getPostId(), event.getAuthorId());
        try {
            aiModerationService.evaluateNewPost(event.getPostId(), event.getAuthorId(), event.getContent());
        } catch (Exception e) {
            log.error("Failed to auto-moderate new post {}: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
