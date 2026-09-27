package iuh.fit.aiservice.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendWarningNotification(UUID recipientId, String title, String content, String targetId) {
        if (recipientId == null) {
            log.warn("Cannot send warning notification: recipientId is null");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId", recipientId.toString());
            payload.put("actorId", null);
            payload.put("type", "SYSTEM");
            payload.put("title", title);
            payload.put("content", content);
            payload.put("targetId", targetId != null ? targetId : "");
            payload.put("targetUrl", targetId != null ? "/posts/" + targetId : "");
            payload.put("avatarUrl", "");

            kafkaTemplate.send("notification.in-app.send", payload);
            log.info("Dispatched AI warning notification to user {} via Kafka topic 'notification.in-app.send'", recipientId);
        } catch (Exception e) {
            log.error("Failed to dispatch AI warning notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
