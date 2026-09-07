package iuh.fit.postservice.application.features.comment.commands.create_comment;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCommentCommand {
    UUID postId;
    UUID authorId;
    UUID parentCommentId;
    String content;
    MultipartFile file;
}
