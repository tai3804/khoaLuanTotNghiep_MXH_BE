package iuh.fit.notificationservice.application.features.notification_setting.commands.update_settings;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateNotificationSettingsCommand {

    UUID userId;
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
