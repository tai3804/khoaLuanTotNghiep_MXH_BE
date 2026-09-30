package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import lombok.Data;

@Data
public class UpdateGroupRequest {
    String name;
    String description;
    String coverUrl;
    GroupPrivacy privacy;
    Boolean postApprovalRequired;
    String rules;
}
