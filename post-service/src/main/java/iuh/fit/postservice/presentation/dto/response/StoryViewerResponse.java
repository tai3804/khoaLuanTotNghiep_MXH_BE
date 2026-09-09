package iuh.fit.postservice.presentation.dto.response;

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
public class StoryViewerResponse {
    UUID id;
    UUID storyId;
    UUID viewerId;
    Instant viewedAt;
}
