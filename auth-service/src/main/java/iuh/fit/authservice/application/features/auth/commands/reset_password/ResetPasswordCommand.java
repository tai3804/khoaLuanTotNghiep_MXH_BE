package iuh.fit.authservice.application.features.auth.commands.reset_password;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPasswordCommand {
    String email;
    String resetToken;
    String newPassword;
}
