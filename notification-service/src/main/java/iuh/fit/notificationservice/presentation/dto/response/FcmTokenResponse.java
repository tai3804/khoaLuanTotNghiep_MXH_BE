package iuh.fit.notificationservice.presentation.dto.response;

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
public class FcmTokenResponse {
    UUID id;
    UUID userId;
    String fcmToken;
    String deviceType;
    Instant createdAt;
}
