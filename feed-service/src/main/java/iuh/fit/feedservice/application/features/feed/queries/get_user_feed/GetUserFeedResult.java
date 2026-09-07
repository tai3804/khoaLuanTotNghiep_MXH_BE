package iuh.fit.feedservice.application.features.feed.queries.get_user_feed;

import iuh.fit.feedservice.infrastructure.client.dto.PostResponseDto;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserFeedResult {
    UUID feedItemId;
    UUID postId;
    UUID authorId;
    PostResponseDto postDetail;
    LocalDateTime createdAt;
}
