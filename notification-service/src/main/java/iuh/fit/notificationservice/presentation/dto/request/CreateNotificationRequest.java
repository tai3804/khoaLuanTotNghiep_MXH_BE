package iuh.fit.notificationservice.presentation.dto.request;

import iuh.fit.notificationservice.domain.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateNotificationRequest {

    @NotNull(message = "Recipient ID is required")
    UUID recipientId;

    @NotNull(message = "Notification type is required")
    NotificationType type;

    @NotBlank(message = "Title is required")
    String title;

    @NotBlank(message = "Content is required")
    String content;

    String targetId;
    String targetUrl;
    String avatarUrl;
}
