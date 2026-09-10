package iuh.fit.notificationservice.application.features.notification_setting.queries.get_settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetNotificationSettingsQuery {
    private UUID userId;
}
