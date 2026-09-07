package iuh.fit.feedservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum FeedServiceErrorCode implements BaseError {

    FEED_ITEM_NOT_FOUND(404, "Feed item not found", 404),
    UNAUTHORIZED(401, "Unauthenticated", 401);

    int code;
    String message;
    int statusCode;
}
