package iuh.fit.aiservice.infrastructure.event;

import iuh.fit.commonframework.event.PostFlaggedEvent;
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

    /**
     * Gắn cờ bài viết nghi vấn cho kiểm duyệt viên: admin-service nhận sự kiện và đưa bài vào hàng chờ kiểm duyệt.
     */
    public void publishPostFlagged(PostFlaggedEvent event) {
        if (event == null || event.getPostId() == null) return;

        try {
            kafkaTemplate.send("post.flagged", event);
            log.info("Published PostFlaggedEvent to 'post.flagged' for postId: {}, score: {}, category: {}",
                    event.getPostId(), event.getScore(), event.getCategory());
        } catch (Exception e) {
            log.error("Failed to publish PostFlaggedEvent for postId {}: {}", event.getPostId(), e.getMessage(), e);
        }
    }
}
