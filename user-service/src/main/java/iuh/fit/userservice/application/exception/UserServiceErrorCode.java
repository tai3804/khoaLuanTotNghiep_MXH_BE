package iuh.fit.userservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum UserServiceErrorCode implements BaseError {
    RESOURCE_NOT_FOUND(404, "Resource not found", 404),
    PROFILE_ALREADY_EXISTS(400, "User profile already exists", 400),
    USER_PROFILE_NOT_FOUND(404, "User profile not found", 404),
    CANNOT_CONNECT_SELF(400, "Cannot connect or follow yourself", 400),
    CONNECTION_ALREADY_EXISTS(400, "Connection or request already exists", 400),
    CONNECTION_NOT_FOUND(404, "Connection or request not found", 404),
    UNAUTHORIZED(401, "Unauthorized access", 401),
    // Nhóm cộng đồng: lỗi quyền phải là 403 (401 làm FE tưởng hết phiên đăng nhập)
    FORBIDDEN(403, "Bạn không có quyền thực hiện thao tác này", 403),
    GROUP_NOT_FOUND(404, "Không tìm thấy nhóm hoặc nhóm đã bị xóa", 404),
    GROUP_MEMBER_NOT_FOUND(404, "Không tìm thấy thành viên trong nhóm", 404),
    GROUP_MEMBER_BANNED(403, "Bạn đã bị cấm khỏi nhóm này nên không thể tham gia lại", 403),
    GROUP_MEMBERS_PRIVATE(403, "Chỉ thành viên của nhóm riêng tư mới xem được danh sách thành viên", 403),
    GROUP_OWNER_CANNOT_LEAVE(400, "Bạn là chủ nhóm. Hãy chuyển quyền chủ nhóm cho thành viên khác trước khi rời nhóm", 400),
    GROUP_OWNER_PROTECTED(403, "Không thể thay đổi vai trò hay xóa chủ nhóm", 403),
    GROUP_ADMIN_PROTECTED(403, "Người kiểm duyệt không thể xóa hoặc cấm quản trị viên", 403),
    GROUP_NAME_DUPLICATED(400, "Bạn đã có một nhóm trùng tên này, hãy chọn tên khác", 400),
    GROUP_TRANSFER_TARGET_INVALID(400, "Người nhận quyền chủ nhóm phải là thành viên đã được duyệt của nhóm", 400),
    GROUP_INVALID_SCOPE(400, "Phạm vi danh sách nhóm không hợp lệ (chỉ nhận: all, discover, joined, pending)", 400),
    GROUP_JOIN_COOLDOWN(429,"Yêu cầu tham gia của bạn vừa bị từ chối, vui lòng thử lại sau 24 giờ", 429);

    int code;
    String message;
    int statusCode;
}
