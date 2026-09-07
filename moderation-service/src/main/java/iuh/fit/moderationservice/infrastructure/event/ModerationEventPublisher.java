package iuh.fit.moderationservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModerationEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPostModerated(UUID postId, String action, String reason, UUID moderatorId) {
        PostModeratedEvent event = PostModeratedEvent.builder()
                .postId(postId)
                .action(action)
                .reason(reason)
                .moderatorId(moderatorId)
                .build();

        kafkaTemplate.send("post.moderated", event);
        log.info("Published PostModeratedEvent for postId: {}, action: {}", postId, action);
    }
}
