package iuh.fit.postservice.presentation.dto.response;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostResponse {
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
    List<PostMediaResponse> mediaList;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
