package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePostResult {
    UUID id;
    UUID authorId;
    String content;
    PostPrivacy privacy;
    Set<UUID> allowedUserIds;
    List<PostMedia> mediaList;
    LocalDateTime updatedAt;
}
