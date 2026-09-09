package iuh.fit.notificationservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class SendPushNotificationRequest {

    @NotNull(message = "Target user ID is required")
    UUID targetUserId;

    @NotBlank(message = "Notification title is required")
    String title;

    @NotBlank(message = "Notification body is required")
    String body;

    Map<String, String> data;
}
