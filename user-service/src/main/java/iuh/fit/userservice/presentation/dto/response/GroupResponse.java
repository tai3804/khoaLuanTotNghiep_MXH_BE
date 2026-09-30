package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;

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
    List<UUID> memberIds;
}

