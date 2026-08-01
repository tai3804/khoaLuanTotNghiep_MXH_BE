package iuh.fit.authservice.application.features.auth.commands.login_user;

import iuh.fit.authservice.domain.enums.MfaType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginUserResult {
    String accessToken;
    String refreshToken;
    UserResult user;
    Boolean mfaRequired;
    String mfaToken;
    MfaType mfaType;
}
