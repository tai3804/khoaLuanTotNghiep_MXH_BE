package iuh.fit.moderationservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ModerationServiceErrorCode implements BaseError {

    REPORT_NOT_FOUND(404, "Report not found", 404),
    REPORT_ALREADY_SUBMITTED(400, "You have already reported this item", 400),
    INVALID_MODERATION_ACTION(400, "Invalid moderation action for target type", 400),
    UNAUTHORIZED_MODERATOR(403, "Only authorized moderators can perform this action", 403),
    UNAUTHORIZED(401, "Unauthenticated", 401);

    int code;
    String message;
    int statusCode;
}
