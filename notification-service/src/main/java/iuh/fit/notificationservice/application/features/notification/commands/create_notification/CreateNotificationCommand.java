package iuh.fit.notificationservice.application.features.notification.commands.create_notification;

import iuh.fit.notificationservice.domain.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateNotificationCommand {
    UUID recipientId;
    UUID actorId;
    NotificationType type;
    String title;
    String content;
    String targetId;
    String targetUrl;
    String avatarUrl;
}
