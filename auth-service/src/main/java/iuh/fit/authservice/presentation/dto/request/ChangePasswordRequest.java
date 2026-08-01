package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank(message = "{password.old.required}")
    String oldPassword;

    @NotBlank(message = "{password.new.required}")
    @Size(min = 6, message = "{password.new.size}")
    String newPassword;
}
