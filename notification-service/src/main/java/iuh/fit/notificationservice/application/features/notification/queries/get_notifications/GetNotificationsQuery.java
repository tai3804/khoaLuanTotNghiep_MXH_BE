package iuh.fit.notificationservice.application.features.notification.queries.get_notifications;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetNotificationsQuery {
    UUID recipientId;
    BaseFilter filter;
}
