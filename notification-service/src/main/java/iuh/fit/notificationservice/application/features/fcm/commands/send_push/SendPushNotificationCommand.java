package iuh.fit.notificationservice.application.features.fcm.commands.send_push;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendPushNotificationCommand {
    UUID targetUserId;
    String title;
    String body;
    Map<String, String> data;
}
