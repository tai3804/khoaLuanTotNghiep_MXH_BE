package iuh.fit.authservice.application.features.auth.commands.login_user;

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
}
