package iuh.fit.postservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum PostServiceErrorCode implements BaseError {

    UNAUTHORIZED(40101, "Unauthorized access", 401),
    POST_NOT_FOUND(40401, "Post not found", 404),
    COMMENT_NOT_FOUND(40402, "Comment not found", 404),
    UNAUTHORIZED_ACTION(40301, "You are not authorized to perform this action", 403),
    INVALID_POST_CONTENT(40001, "Post content or media must be provided", 400),
    MEDIA_UPLOAD_FAILED(50001, "Failed to process media files", 500);

    int code;
    String message;
    int statusCode;
}
