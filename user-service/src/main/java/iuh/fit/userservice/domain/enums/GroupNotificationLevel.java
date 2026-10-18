package iuh.fit.userservice.domain.enums;

/** Mức nhận thông báo hoạt động của nhóm do từng thành viên tự chọn (giống "Cài đặt thông báo" của Facebook). */
public enum GroupNotificationLevel {
    /** Thông báo mọi bài viết mới. */
    ALL,
    /** Chỉ thông báo những việc liên quan tới mình (mặc định). */
    HIGHLIGHTS,
    /** Tắt thông báo hoạt động của nhóm. */
    OFF
}
