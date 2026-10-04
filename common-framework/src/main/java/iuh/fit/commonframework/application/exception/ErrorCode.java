package iuh.fit.commonframework.application.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode implements BaseError {

    UNCATEGORIZED_EXCEPTION(500, "Đã có lỗi xảy ra trong hệ thống. Vui lòng thử lại sau.", 500),
    INVALID_KEY(400, "Khóa hoặc tham số không hợp lệ", 400),
    UNAUTHENTICATED(401, "Phiên đăng nhập đã hết hạn hoặc chưa xác thực", 401),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện thao tác này", 403),
    NOT_FOUND(404, "Không tìm thấy tài nguyên yêu cầu", 404),
    INVALID_INPUT(400, "Dữ liệu đầu vào không hợp lệ", 400);

    int code;
    String message;
    int statusCode;
}
