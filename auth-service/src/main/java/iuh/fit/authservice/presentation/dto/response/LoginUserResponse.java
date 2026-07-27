package iuh.fit.authservice.presentation.dto.response;

import iuh.fit.authservice.application.features.auth.commands.login_user.UserResult;
import iuh.fit.authservice.domain.enums.MfaType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginUserResponse {
    String accessToken;
    String refreshToken;
    UserResult user;
    Boolean mfaRequired;
    String mfaToken;
    MfaType mfaType;
}
