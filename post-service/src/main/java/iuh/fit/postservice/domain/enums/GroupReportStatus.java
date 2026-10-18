package iuh.fit.postservice.domain.enums;

/** Trạng thái một báo cáo bài viết gửi tới quản trị viên nhóm. */
public enum GroupReportStatus {
    /** Đang chờ quản trị viên xử lý. */
    OPEN,
    /** Quản trị viên giữ bài viết (báo cáo không đúng). */
    DISMISSED,
    /** Quản trị viên đã gỡ bài viết. */
    ACTIONED
}
