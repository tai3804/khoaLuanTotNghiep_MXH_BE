package iuh.fit.notificationservice.application.features.fcm.commands.unregister_token;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnregisterFcmTokenCommand {
    UUID userId;
    String fcmToken;
}
