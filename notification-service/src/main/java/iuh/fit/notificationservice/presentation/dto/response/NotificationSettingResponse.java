package iuh.fit.notificationservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationSettingResponse {

    UUID id;
    UUID userId;
    boolean likePost;
    boolean commentPost;
    boolean sharePost;
    boolean friendRequest;
    boolean message;
    boolean call;
    boolean system;
    boolean sound;
    boolean emailNotification;
}
