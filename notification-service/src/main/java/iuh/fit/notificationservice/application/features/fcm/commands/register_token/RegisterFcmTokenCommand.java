package iuh.fit.notificationservice.application.features.fcm.commands.register_token;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterFcmTokenCommand {
    UUID userId;
    String fcmToken;
    String deviceType;
}
