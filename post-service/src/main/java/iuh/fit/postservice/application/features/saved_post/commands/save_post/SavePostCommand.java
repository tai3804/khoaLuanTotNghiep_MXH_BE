package iuh.fit.postservice.application.features.saved_post.commands.save_post;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavePostCommand {
    UUID userId;
    UUID postId;
    String collectionName;
}
