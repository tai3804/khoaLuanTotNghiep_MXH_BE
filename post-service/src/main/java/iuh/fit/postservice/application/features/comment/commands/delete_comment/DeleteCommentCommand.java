package iuh.fit.postservice.application.features.comment.commands.delete_comment;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteCommentCommand {
    UUID commentId;
    UUID userId;
}
