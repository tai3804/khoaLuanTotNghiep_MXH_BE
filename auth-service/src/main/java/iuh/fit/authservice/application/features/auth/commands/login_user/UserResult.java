package iuh.fit.authservice.application.features.auth.commands.login_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResult {
    String id;
    String email;
    String firstName;
    String lastName;
    String middleName;
    Set<String> roles;
    Set<String> permissions;
}
