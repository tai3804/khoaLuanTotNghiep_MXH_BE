package iuh.fit.postservice.application.features.comment.commands.update_comment;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class UpdateCommentCommand {
    UUID postId;
    UUID commentId;
    UUID userId;
    String content;
}
