package iuh.fit.authservice.application.features.auth.commands.register_user;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserCommand {
    String email;
    String password;
    String fullName;
}
