package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
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
}
