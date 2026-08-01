package iuh.fit.authservice.application.features.auth.commands.mfa_setup;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaSetupResult {
    String secretKey;
    String qrCodeUrl;
}
