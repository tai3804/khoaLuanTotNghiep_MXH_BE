package iuh.fit.postservice.infrastructure.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Báo cho user-service biết một quyết định kiểm duyệt ảnh hưởng tới điểm uy tín của tác giả.
 * post-service chỉ nêu sự kiện (hành động + bài viết); mức trừ điểm do user-service quyết định.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReputationEventPublisher {

    static final String TOPIC = "user.reputation.adjust";

    KafkaTemplate<String, Object> kafkaTemplate;

    /** action: WARN_USER | AUTO_HIDE | HIDE_POST | DELETE_POST. */
    public void penalize(UUID userId, String action, UUID postId, String reason, UUID actorId) {
        send("PENALTY", userId, action, postId, reason, actorId);
    }

    /** Thu hồi mọi khoản trừ điểm gắn với bài viết (bài được khôi phục / kháng nghị được chấp nhận). */
    public void revert(UUID userId, UUID postId, String reason) {
        send("REVERT", userId, null, postId, reason, null);
    }

    private void send(String type, UUID userId, String action, UUID postId, String reason, UUID actorId) {
        if (userId == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", type);
            event.put("userId", userId.toString());
            event.put("action", action);
            event.put("sourceType", "POST");
            event.put("sourceId", postId != null ? postId.toString() : null);
            event.put("reason", reason);
            event.put("actorId", actorId != null ? actorId.toString() : null);
            kafkaTemplate.send(TOPIC, event);
            log.info("Published reputation {} for user {} (action: {}, post: {})", type, userId, action, postId);
        } catch (Exception e) {
            log.error("Failed to publish reputation {} for user {}: {}", type, userId, e.getMessage());
        }
    }
}
