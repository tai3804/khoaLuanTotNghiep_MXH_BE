package iuh.fit.postservice.application.features.post.commands.delete_post;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeletePostCommand {
    UUID postId;
    UUID userId;
}
