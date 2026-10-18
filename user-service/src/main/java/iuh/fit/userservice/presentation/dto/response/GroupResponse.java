package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupPrivacy;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupResponse {
    UUID id;
    String name;
    String description;
    String coverUrl;
    GroupPrivacy privacy;
    UUID creatorId;
    UUID ownerId;
    long memberCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    boolean postApprovalRequired;
    String rules;
    boolean isMember;
    boolean isAdmin;
    boolean isModerator;
    GroupMemberStatus joinStatus;

    /** Câu hỏi dành cho người xin vào nhóm riêng tư (rỗng nếu nhóm không đặt câu hỏi). */
    List<String> membershipQuestions;
    /** Mức thông báo mình đã chọn trong nhóm (chỉ có khi là thành viên). */
    GroupNotificationLevel notificationLevel;
    /** Tên người đã mời mình (chỉ có khi joinStatus = INVITED). */
    String invitedByName;
    /** Số yêu cầu tham gia đang chờ duyệt: chỉ trả cho quản trị/kiểm duyệt viên và chỉ ở chi tiết một nhóm. */
    Long pendingRequestCount;
    /** Số thành viên mới trong 7 ngày qua (chỉ ở chi tiết một nhóm; thẻ "Hoạt động của nhóm"). */
    Long newMembersThisWeek;
    /** Vài thành viên đầu để hiển thị dải ảnh đại diện (chi tiết một nhóm, khi mình được xem danh sách thành viên). */
    List<MemberPreview> memberPreview;

    @Data
    @Builder
    public static class MemberPreview {
        UUID userId;
        String name;
        String avatarUrl;
    }
}
