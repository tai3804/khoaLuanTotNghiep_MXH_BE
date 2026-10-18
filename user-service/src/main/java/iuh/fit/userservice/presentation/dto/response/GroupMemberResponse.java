package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupMemberResponse {
    UUID userId;
    String name;
    String avatarUrl;
    GroupRole role;
    GroupMemberStatus status;
    LocalDateTime joinedAt;
    /** Câu trả lời khi xin vào nhóm: chỉ quản trị/kiểm duyệt viên nhìn thấy, và chỉ ở danh sách chờ duyệt. */
    List<String> answers;
    /** Người đã mời (chỉ ở danh sách lời mời đang chờ). */
    String invitedByName;
}
