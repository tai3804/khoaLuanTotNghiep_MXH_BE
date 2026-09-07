package iuh.fit.feedservice.application.features.feed.commands.process_post_created;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessPostCreatedCommand {
    UUID postId;
    UUID authorId;
    LocalDateTime createdAt;
}
