package iuh.fit.authservice.application.features.auth.commands.forgot_password;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForgotPasswordCommand {
    String email;
}
