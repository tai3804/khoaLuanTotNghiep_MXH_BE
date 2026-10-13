package iuh.fit.postservice.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    List<PostMediaResponse> mediaList;
    Set<UUID> taggedUserIds;
    Set<String> hashtags;
    // Giữ key JSON là isPinned/isArchived (FE và UpdatePostRequest dùng tên này); nếu không Jackson sẽ xuất "pinned"/"archived"
    @JsonProperty("isPinned")
    boolean pinned;
    @JsonProperty("isArchived")
    boolean archived;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Double relevanceScore;
    String relevanceReason;
}
