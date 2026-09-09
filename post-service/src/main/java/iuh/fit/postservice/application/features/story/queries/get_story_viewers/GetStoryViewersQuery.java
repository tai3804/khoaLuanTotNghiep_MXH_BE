package iuh.fit.postservice.application.features.story.queries.get_story_viewers;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetStoryViewersQuery {
    UUID storyId;
    UUID currentUserId;
    BaseFilter filter;
}
