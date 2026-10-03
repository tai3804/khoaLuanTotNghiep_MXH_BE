package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostModerationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPostModerated(UUID postId, String action, String reason) {
        if (postId == null) return;

        try {
            PostModeratedEvent event = PostModeratedEvent.builder()
                    .postId(postId)
                    .action(action)
                    .reason(reason)
                    .moderatorId(null)
                    .build();

            kafkaTemplate.send("post.moderated", event);
            log.info("Published PostModeratedEvent to 'post.moderated' for postId: {}, action: {}", postId, action);
        } catch (Exception e) {
            log.error("Failed to publish PostModeratedEvent for postId {}: {}", postId, e.getMessage(), e);
        }
    }
}
