package iuh.fit.userservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum UserServiceErrorCode implements BaseError {
    RESOURCE_NOT_FOUND(404, "Resource not found", 404),
    PROFILE_ALREADY_EXISTS(400, "User profile already exists", 400),
    USER_PROFILE_NOT_FOUND(404, "User profile not found", 404),
    CANNOT_CONNECT_SELF(400, "Cannot connect or follow yourself", 400),
    CONNECTION_ALREADY_EXISTS(400, "Connection or request already exists", 400),
    CONNECTION_NOT_FOUND(404, "Connection or request not found", 404),
    UNAUTHORIZED(401, "Unauthorized access", 401);

    int code;
    String message;
    int statusCode;
}
