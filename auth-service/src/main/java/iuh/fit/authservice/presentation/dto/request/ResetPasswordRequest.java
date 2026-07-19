package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPasswordRequest {
    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    String email;

    @NotBlank(message = "{password.resetToken.required}")
    String resetToken;

    @NotBlank(message = "{password.new.required}")
    String newPassword;
}
