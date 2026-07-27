package iuh.fit.authservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AuthErrorCode implements BaseError {
    INVALID_CREDENTIALS(401, "Invalid email or password", 401),
    USER_NOT_FOUND(404, "User not found", 404),
    EMAIL_ALREADY_EXISTS(400, "Email already exists", 400),
    EMAIL_NOT_FOUND(404, "Email not found", 404),
    INVALID_PASSWORD(400, "Password is incorrect", 400),
    DEVICE_NOT_FOUND(404, "Device not found", 404),
    INVALID_TOKEN(401, "Invalid or expired token", 401),
    REFRESH_TOKEN_EXPIRED(401, "Refresh token has expired", 401),
    UNAUTHORIZED(401, "Unauthorized access", 401),
    INVALID_OTP(400, "Invalid or expired OTP code", 400),
    INVALID_MFA_TYPE(400, "Invalid MFA type. Must be TOTP or EMAIL", 400);

    int code;
    String message;
    int statusCode;
}
