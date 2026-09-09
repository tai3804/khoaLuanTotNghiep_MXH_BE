package iuh.fit.postservice.presentation.dto.response;

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
public class UserStoriesResponse {
    UUID userId;
    boolean hasUnviewedStories;
    List<StoryResponse> stories;
}
