package iuh.fit.adminservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AdminServiceErrorCode implements BaseError {
    RESOURCE_NOT_FOUND(404, "Không tìm thấy tài nguyên yêu cầu", 404),
    REPORT_NOT_FOUND(404, "Không tìm thấy thông tin báo cáo", 404),
    REPORT_ALREADY_SUBMITTED(400, "Bạn đã gửi báo cáo cho nội dung này rồi và đang chờ quản trị viên xem xét.", 400),
    UNAUTHORIZED(401, "Bạn không có quyền truy cập", 401);

    int code;
    String message;
    int statusCode;
}
