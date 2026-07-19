package iuh.fit.authservice.application.features.auth.commands.login_user;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginUserCommand {
    String email;
    String password;
    String deviceFingerprint;
    String deviceName;
    String ipAddress;
}
