package iuh.fit.notificationservice.infrastructure.kafka.consumers;

import iuh.fit.notificationservice.infrastructure.firebase.FcmPushNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushNotificationEventListener {

    FcmPushNotificationService pushNotificationService;

    @KafkaListener(topics = "notification.push.send", groupId = "notification-service-group")
    public void handlePushNotificationEvent(Map<String, Object> event) {
        log.info("Received Kafka push notification event: {}", event);

        try {
            String targetUserIdStr = (String) event.get("targetUserId");
            String title = (String) event.get("title");
            String body = (String) event.get("body");
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) event.get("data");

            if (targetUserIdStr != null && title != null && body != null) {
                UUID targetUserId = UUID.fromString(targetUserIdStr);
                int sent = pushNotificationService.sendPushNotification(targetUserId, title, body, data);
                log.info("Processed Kafka FCM push notification event for user {}: sent to {} tokens", targetUserId, sent);
            } else {
                log.warn("Invalid payload received for FCM push notification event: {}", event);
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka push notification event: {}", e.getMessage(), e);
        }
    }
}
