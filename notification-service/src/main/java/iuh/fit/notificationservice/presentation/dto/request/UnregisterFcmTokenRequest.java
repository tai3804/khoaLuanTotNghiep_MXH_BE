package iuh.fit.notificationservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnregisterFcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    String fcmToken;
}
