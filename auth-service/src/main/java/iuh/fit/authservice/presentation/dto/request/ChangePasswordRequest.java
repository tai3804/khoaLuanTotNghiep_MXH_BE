package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank(message = "{password.old.required}")
    String oldPassword;

    @NotBlank(message = "{password.new.required}")
    String newPassword;
}
