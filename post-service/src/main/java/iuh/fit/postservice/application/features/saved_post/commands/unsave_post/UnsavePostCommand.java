package iuh.fit.postservice.application.features.saved_post.commands.unsave_post;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnsavePostCommand {
    UUID userId;
    UUID postId;
}
