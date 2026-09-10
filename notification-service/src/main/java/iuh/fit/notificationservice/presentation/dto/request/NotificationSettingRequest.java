package iuh.fit.notificationservice.presentation.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationSettingRequest {

    Boolean likePost;
    Boolean commentPost;
    Boolean sharePost;
    Boolean friendRequest;
    Boolean message;
    Boolean call;
    Boolean system;
    Boolean sound;
    Boolean emailNotification;
}
