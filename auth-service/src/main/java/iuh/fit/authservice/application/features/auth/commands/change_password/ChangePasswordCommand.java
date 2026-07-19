package iuh.fit.authservice.application.features.auth.commands.change_password;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordCommand {
    UUID userId;
    String oldPassword;
    String newPassword;
}
