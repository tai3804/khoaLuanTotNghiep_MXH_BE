package iuh.fit.postservice.application.features.story.queries.get_active_stories;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetActiveStoriesQuery {
    UUID currentUserId;
    List<UUID> followingIds;
}
