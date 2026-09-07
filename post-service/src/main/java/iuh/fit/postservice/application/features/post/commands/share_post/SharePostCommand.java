package iuh.fit.postservice.application.features.post.commands.share_post;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SharePostCommand {
    UUID originalPostId;
    UUID userId;
    String caption;
    PostPrivacy privacy;
}
