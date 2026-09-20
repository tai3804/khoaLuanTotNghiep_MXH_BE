package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePostCommand {
    UUID postId;
    UUID userId;
    String content;
    PostPrivacy privacy;
    Set<UUID> allowedUserIds;
    Boolean isPinned;
    Boolean isArchived;
    /**
     * When present, replaces the post attachment list. Omitting this field keeps
     * the existing attachments unchanged.
     */
    List<String> mediaUrls;
}
