package iuh.fit.notificationservice.application.features.notification.commands.send_email;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendEmailCommand {
    String toEmail;
    String resetToken;
}
