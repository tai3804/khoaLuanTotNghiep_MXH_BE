package iuh.fit.authservice.application.features.auth.commands.mfa_verify;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaVerifyCommand {
    String mfaToken;
    String otpCode;
    String deviceFingerprint;
}
