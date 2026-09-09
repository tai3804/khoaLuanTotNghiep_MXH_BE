package iuh.fit.notificationservice.application.features.notification.queries.get_notifications;

import iuh.fit.notificationservice.domain.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResult {
    UUID id;
    UUID recipientId;
    UUID actorId;
    NotificationType type;
    String title;
    String content;
    String targetId;
    String targetUrl;
    String avatarUrl;
    boolean isRead;
    Instant readAt;
    Instant createdAt;
}
