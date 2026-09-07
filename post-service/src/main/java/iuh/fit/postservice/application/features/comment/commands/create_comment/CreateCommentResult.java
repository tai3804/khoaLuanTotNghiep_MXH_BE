package iuh.fit.postservice.application.features.comment.commands.create_comment;

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
public class CreateCommentResult {
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
