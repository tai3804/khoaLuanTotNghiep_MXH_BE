package iuh.fit.userservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupFeedVisibilityResponse {
    private UUID groupId;
    private boolean member;
    private boolean publicGroup;
}
