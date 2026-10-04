package iuh.fit.authservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AuthErrorCode implements BaseError {
    INVALID_CREDENTIALS(401, "Email hoặc mật khẩu không chính xác", 401),
    USER_NOT_FOUND(404, "Không tìm thấy tài khoản người dùng", 404),
    EMAIL_ALREADY_EXISTS(400, "Email này đã được sử dụng", 400),
    EMAIL_NOT_FOUND(404, "Email không tồn tại trong hệ thống", 404),
    INVALID_PASSWORD(400, "Mật khẩu không chính xác", 400),
    DEVICE_NOT_FOUND(404, "Không tìm thấy thiết bị", 404),
    INVALID_TOKEN(401, "Mã token không hợp lệ hoặc đã hết hạn", 401),
    REFRESH_TOKEN_EXPIRED(401, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", 401),
    UNAUTHORIZED(401, "Truy cập không được ủy quyền", 401),
    INVALID_OTP(400, "Mã OTP không hợp lệ hoặc đã hết hạn", 400),
    INVALID_MFA_TYPE(400, "Phương thức xác thực 2 bước không hợp lệ", 400),
    DATE_OF_BIRTH_REQUIRED(400, "Ngày sinh là bắt buộc", 400),
    INVALID_DATE_OF_BIRTH(400, "Ngày sinh không hợp lệ", 400),
    USER_UNDER_13(400, "Bạn phải từ đủ 13 tuổi trở lên mới được đăng ký tài khoản", 400),
    ACCOUNT_BANNED(403, "Tài khoản của bạn đã bị cấm/khóa do vi phạm tiêu chuẩn cộng đồng.", 403),
    ACCOUNT_LOCKED(403, "Tài khoản của bạn tạm thời bị khóa. Vui lòng liên hệ quản trị viên.", 403);

    int code;
    String message;
    int statusCode;
}
