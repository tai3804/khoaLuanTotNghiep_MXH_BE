package iuh.fit.userservice.domain.enums;

public enum GroupMemberStatus {
    PENDING,
    APPROVED,
    REJECTED,
    BANNED,
    /** Đã được một thành viên mời, đang chờ người được mời chấp nhận hoặc từ chối. */
    INVITED
}
