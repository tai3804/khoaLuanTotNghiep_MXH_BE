package iuh.fit.authservice.application.features.auth.commands.logout_user;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogoutUserCommand {
    UUID userId;
    String deviceFingerprint;
}
