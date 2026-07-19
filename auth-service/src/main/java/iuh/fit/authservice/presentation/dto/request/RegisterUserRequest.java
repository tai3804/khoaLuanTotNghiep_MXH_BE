package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserRequest {
    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    String email;

    @NotBlank(message = "{user.password.required}")
    String password;

    @NotBlank(message = "{user.fullName.required}")
    String fullName;
}
