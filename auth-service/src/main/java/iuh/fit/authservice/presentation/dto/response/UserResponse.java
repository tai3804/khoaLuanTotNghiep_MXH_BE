package iuh.fit.authservice.presentation.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String email;
    String firstName;
    String lastName;
    String middleName;
    Set<String> roles;
    Set<String> permissions;
}
