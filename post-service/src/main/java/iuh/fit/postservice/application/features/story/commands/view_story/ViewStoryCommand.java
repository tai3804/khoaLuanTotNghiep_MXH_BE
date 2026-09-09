package iuh.fit.postservice.application.features.story.commands.view_story;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViewStoryCommand {
    UUID storyId;
    UUID viewerId;
}
