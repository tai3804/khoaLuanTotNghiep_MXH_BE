package iuh.fit.adminservice.domain.enums;

public enum ModerationAction {
    HIDE_POST,
    DELETE_POST,
    DELETE_COMMENT,
    WARN_USER,
    BAN_USER,
    DISMISS,
    RESTORE_POST,
    /** Kiểm duyệt viên chấp nhận kháng nghị của tác giả (bài được khôi phục). */
    APPEAL_ACCEPTED,
    /** Kiểm duyệt viên bác bỏ kháng nghị, giữ nguyên quyết định. */
    APPEAL_REJECTED
}
