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
    // Nhóm cộng đồng: thiếu quyền trong nhóm là 403 (401 làm FE tưởng hết phiên đăng nhập)
    GROUP_MEMBERSHIP_REQUIRED(40310, "Bạn cần là thành viên của nhóm này để đăng bài", 403),
    GROUP_PRIVATE(40311, "Đây là nhóm riêng tư: hãy tham gia nhóm để xem nội dung", 403),
    GROUP_SERVICE_UNAVAILABLE(50301, "Chưa kiểm tra được quyền của bạn trong nhóm, vui lòng thử lại sau ít phút", 503),
    GROUP_PIN_LIMIT(40010, "Mỗi nhóm chỉ ghim được tối đa 5 bài viết, hãy bỏ ghim bớt một bài", 400),
    GROUP_POST_NOT_PINNABLE(40011, "Chỉ ghim được bài viết đã đăng trong nhóm", 400),
    INVALID_POST_CONTENT(40001, "Post content or media must be provided", 400),
    MEDIA_UPLOAD_FAILED(50001, "Failed to process media files", 500),
    POLL_NOT_FOUND(40403, "Poll not found for this post", 404),
    POLL_CLOSED(40002, "Poll is closed or has expired", 400);

    int code;
    String message;
    int statusCode;
}
