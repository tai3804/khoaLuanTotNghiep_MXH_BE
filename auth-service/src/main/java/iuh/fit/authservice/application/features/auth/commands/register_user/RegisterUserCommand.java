package iuh.fit.authservice.application.features.auth.commands.register_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import java.time.LocalDate;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserCommand {
    String email;
    String password;
    String fullName;
    LocalDate dateOfBirth;
    String gender;
}
