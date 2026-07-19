package iuh.fit.authservice.presentation.dto.response;

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
    UserResponse user;
}
