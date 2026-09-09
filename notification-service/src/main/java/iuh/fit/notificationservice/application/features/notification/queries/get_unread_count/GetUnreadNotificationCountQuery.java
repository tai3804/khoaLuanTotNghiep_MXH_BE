package iuh.fit.notificationservice.application.features.notification.queries.get_unread_count;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUnreadNotificationCountQuery {
    UUID recipientId;
}
