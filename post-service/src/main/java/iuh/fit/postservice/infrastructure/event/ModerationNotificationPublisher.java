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

/** Gửi thông báo hệ thống tới tác giả bài viết khi có quyết định kiểm duyệt. */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModerationNotificationPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    public void send(UUID recipientId, String actorId, String title, String content, String targetId, String targetUrl) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId", recipientId.toString());
            payload.put("actorId", actorId);
            payload.put("type", "SYSTEM");
            payload.put("title", title);
            payload.put("content", content);
            payload.put("targetId", targetId != null ? targetId : "");
            payload.put("targetUrl", targetUrl != null ? targetUrl : "");
            payload.put("avatarUrl", "");

            kafkaTemplate.send("notification.in-app.send", payload);
            log.info("Dispatched moderation notification to author {} via 'notification.in-app.send'", recipientId);
        } catch (Exception e) {
            log.error("Failed to dispatch moderation notification to author {}: {}", recipientId, e.getMessage());
        }
    }
}
