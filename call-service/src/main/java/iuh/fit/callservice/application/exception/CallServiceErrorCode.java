package iuh.fit.callservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum CallServiceErrorCode implements BaseError {

    CALL_SESSION_NOT_FOUND(404, "Call session not found", 404),
    USER_ALREADY_IN_CALL(400, "User is already in an active call", 400),
    NOT_A_CALL_PARTICIPANT(403, "You are not a participant in this call session", 403),
    UNAUTHORIZED_CALL_HOST(403, "Only the call host can perform this action", 403),
    CALL_SESSION_ALREADY_ENDED(400, "Call session has already ended", 400),
    CANNOT_CALL_SELF(400, "Cannot initiate a call with yourself", 400),
    UNAUTHORIZED(401, "Unauthenticated", 401);

    int code;
    String message;
    int statusCode;
}
