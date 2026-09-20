package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.GroupPrivacy;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
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
    long memberCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

