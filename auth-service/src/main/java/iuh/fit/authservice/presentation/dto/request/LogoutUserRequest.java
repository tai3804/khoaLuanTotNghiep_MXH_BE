package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogoutUserRequest {
    @NotBlank(message = "{device.fingerprint.required}")
    String deviceFingerprint;
}
