package iuh.fit.postservice.application.features.story.queries.get_story_archive;

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
public class GetStoryArchiveQuery {
    UUID currentUserId;
    BaseFilter filter;
}
