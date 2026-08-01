package iuh.fit.authservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaVerifyRequest {
    @NotBlank(message = "{mfa.token.required}")
    String mfaToken;
    @NotBlank(message = "{mfa.otpCode.required}")
    String otpCode;
    String deviceFingerprint;
}
