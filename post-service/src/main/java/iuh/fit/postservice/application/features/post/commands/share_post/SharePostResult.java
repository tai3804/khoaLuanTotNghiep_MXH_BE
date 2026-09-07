package iuh.fit.postservice.application.features.post.commands.share_post;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SharePostResult {
    UUID id;
    UUID authorId;
    UUID originalPostId;
    String content;
    PostPrivacy privacy;
    long shareCount;
    LocalDateTime createdAt;
}
