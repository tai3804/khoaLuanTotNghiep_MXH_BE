package iuh.fit.notificationservice.application.features.notification.commands.mark_read;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarkNotificationAsReadCommand {
    UUID notificationId;
    UUID recipientId;
}
