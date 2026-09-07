package iuh.fit.postservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    UUID id;
    UUID postId;
    UUID authorId;
    UUID parentCommentId;
    String content;
    String mediaUrl;
    String mediaKey;
    long likeCount;
    long replyCount;
    LocalDateTime createdAt;
}
