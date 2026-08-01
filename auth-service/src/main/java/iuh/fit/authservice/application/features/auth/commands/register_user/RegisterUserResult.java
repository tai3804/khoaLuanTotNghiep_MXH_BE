package iuh.fit.authservice.application.features.auth.commands.register_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserResult {
    UUID id;
    String email;
    String firstName;
    String lastName;
    String middleName;
    Set<String> roles;
    Set<String> permissions;
}
