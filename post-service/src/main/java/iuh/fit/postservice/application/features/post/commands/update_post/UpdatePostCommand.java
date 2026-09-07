package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
}
