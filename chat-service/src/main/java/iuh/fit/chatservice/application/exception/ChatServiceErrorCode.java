package iuh.fit.chatservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ChatServiceErrorCode implements BaseError {
    // TODO: Add service-specific error codes
    RESOURCE_NOT_FOUND(404, "Resource not found", 404);

    int code;
    String message;
    int statusCode;
}
