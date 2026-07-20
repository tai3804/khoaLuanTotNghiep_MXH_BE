package iuh.fit.commonframework.application.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode implements BaseError {

    UNCATEGORIZED_EXCEPTION(500, "Uncategorized error", 500),
    INVALID_KEY(400, "Invalid key", 400),
    UNAUTHENTICATED(401, "Unauthenticated", 401),
    UNAUTHORIZED(403, "You do not have permission", 403),
    NOT_FOUND(404, "Resource not found", 404),
    INVALID_INPUT(400, "Invalid input data", 400);

    int code;
    String message;
    int statusCode;
}
