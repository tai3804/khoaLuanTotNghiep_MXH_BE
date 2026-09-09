package iuh.fit.postservice.application.features.story.commands.delete_story;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteStoryCommand {
    UUID storyId;
    UUID currentUserId;
}
