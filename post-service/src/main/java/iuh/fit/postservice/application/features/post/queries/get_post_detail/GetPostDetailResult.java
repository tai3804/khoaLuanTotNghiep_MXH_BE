package iuh.fit.postservice.application.features.post.queries.get_post_detail;

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
public class GetPostDetailResult {
    UUID id;
    UUID authorId;
    UUID groupId;
    String content;
    String summary;
    PostPrivacy privacy;
    PostStatus status;
    Set<UUID> allowedUserIds;
    UUID originalPostId;
    long likeCount;
    long commentCount;
    long shareCount;
    List<PostMedia> mediaList;
    Set<UUID> taggedUserIds;
    Set<String> hashtags;
    // Tên trường không có tiền tố "is": Lombok bỏ tiền tố này khỏi getter/setter nên MapStruct (và builder) mới khớp với Post
    boolean pinned;
    boolean archived;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    /** Chỉ có khi bảng tin được xếp hạng (sortBy=relevance|trending): điểm và lý do bài xuất hiện (friend, following, group, interacted, popular, recent, own, trending). */
    Double relevanceScore;
    String relevanceReason;
}
