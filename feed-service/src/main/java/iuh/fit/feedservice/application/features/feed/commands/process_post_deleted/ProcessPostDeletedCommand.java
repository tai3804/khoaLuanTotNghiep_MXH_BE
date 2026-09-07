package iuh.fit.feedservice.application.features.feed.commands.process_post_deleted;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessPostDeletedCommand {
    UUID postId;
}
