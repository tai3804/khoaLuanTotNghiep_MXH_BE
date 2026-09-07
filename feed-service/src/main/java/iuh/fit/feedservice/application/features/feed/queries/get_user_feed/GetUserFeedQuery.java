package iuh.fit.feedservice.application.features.feed.queries.get_user_feed;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserFeedQuery {
    UUID userId;
    int page;
    int size;
}
