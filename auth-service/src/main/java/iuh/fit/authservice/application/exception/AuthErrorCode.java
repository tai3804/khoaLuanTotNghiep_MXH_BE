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
    INVALID_PASSWORD(400, "Password is incorrect", 400);

    int code;
    String message;
    int statusCode;
}
