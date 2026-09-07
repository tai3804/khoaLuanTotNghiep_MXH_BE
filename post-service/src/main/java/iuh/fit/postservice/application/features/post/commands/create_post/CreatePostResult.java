package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
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
public class CreatePostResult {
    UUID id;
    UUID authorId;
    String content;
    PostPrivacy privacy;
    PostStatus status;
    Set<UUID> allowedUserIds;
    UUID originalPostId;
    long likeCount;
    long commentCount;
    long shareCount;
    List<PostMedia> mediaList;
    LocalDateTime createdAt;
}
