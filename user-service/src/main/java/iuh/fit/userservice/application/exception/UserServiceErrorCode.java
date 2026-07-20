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
    PROFILE_ALREADY_EXISTS(400, "User profile already exists", 400);

    int code;
    String message;
    int statusCode;
}
