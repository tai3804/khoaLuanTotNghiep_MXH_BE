package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupRole;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Quản trị viên / người kiểm duyệt của nhóm: ai cũng xem được, kể cả người chưa vào nhóm riêng tư (như Facebook). */
@Data
@Builder
public class GroupStaffResponse {
    UUID userId;
    String name;
    String avatarUrl;
    GroupRole role;
    boolean owner;
}
