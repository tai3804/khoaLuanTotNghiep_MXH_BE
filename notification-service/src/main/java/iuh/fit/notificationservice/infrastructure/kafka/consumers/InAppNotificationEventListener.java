package iuh.fit.notificationservice.infrastructure.kafka.consumers;

import iuh.fit.notificationservice.application.features.notification.commands.create_notification.CreateNotificationCommand;
import iuh.fit.notificationservice.application.features.notification.commands.create_notification.CreateNotificationHandler;
import iuh.fit.notificationservice.domain.enums.NotificationType;
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
public class InAppNotificationEventListener {

    CreateNotificationHandler createNotificationHandler;

    @KafkaListener(topics = "notification.in-app.send", groupId = "notification-service-group")
    public void handleInAppNotificationEvent(Map<String, Object> event) {
        log.info("Received Kafka in-app notification event: {}", event);

        try {
            String recipientIdStr = (String) event.get("recipientId");
            String actorIdStr = (String) event.get("actorId");
            String typeStr = (String) event.get("type");
            String title = (String) event.get("title");
            String content = (String) event.get("content");
            String targetId = (String) event.get("targetId");
            String targetUrl = (String) event.get("targetUrl");
            String avatarUrl = (String) event.get("avatarUrl");

            if (recipientIdStr != null && title != null && content != null && typeStr != null) {
                UUID recipientId = UUID.fromString(recipientIdStr);
                UUID actorId = actorIdStr != null ? UUID.fromString(actorIdStr) : null;
                NotificationType type = NotificationType.valueOf(typeStr);

                CreateNotificationCommand command = CreateNotificationCommand.builder()
                        .recipientId(recipientId)
                        .actorId(actorId)
                        .type(type)
                        .title(title)
                        .content(content)
                        .targetId(targetId)
                        .targetUrl(targetUrl)
                        .avatarUrl(avatarUrl)
                        .build();

                createNotificationHandler.handle(command);
                log.info("Successfully created in-app notification for recipient {} via Kafka event", recipientId);
            } else {
                log.warn("Invalid payload received for in-app notification event: {}", event);
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka in-app notification event: {}", e.getMessage(), e);
        }
    }
}
