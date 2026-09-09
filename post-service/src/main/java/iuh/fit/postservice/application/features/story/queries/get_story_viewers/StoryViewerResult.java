package iuh.fit.postservice.application.features.story.queries.get_story_viewers;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryViewerResult {
    UUID id;
    UUID storyId;
    UUID viewerId;
    Instant viewedAt;
}
