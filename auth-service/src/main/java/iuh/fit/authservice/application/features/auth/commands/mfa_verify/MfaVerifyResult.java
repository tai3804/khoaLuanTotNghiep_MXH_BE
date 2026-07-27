package iuh.fit.authservice.application.features.auth.commands.mfa_verify;

import iuh.fit.authservice.application.features.auth.commands.login_user.UserResult;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaVerifyResult {
    String accessToken;
    String refreshToken;
    UserResult user;
}
