package iuh.fit.postservice.presentation.dto.request;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePostRequest {
    String content;
    PostPrivacy privacy;
    Set<UUID> allowedUserIds;
    Boolean isPinned;
    Boolean isArchived;
    List<String> mediaUrls;
}
