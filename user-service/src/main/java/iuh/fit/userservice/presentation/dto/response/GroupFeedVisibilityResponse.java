package iuh.fit.userservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import iuh.fit.userservice.domain.enums.GroupRole;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupFeedVisibilityResponse {
    private UUID groupId;
    private boolean member;
    private boolean publicGroup;
    private GroupRole role;
    private boolean postApprovalRequired;
}
