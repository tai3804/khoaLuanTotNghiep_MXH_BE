package iuh.fit.postservice.application.features.post.queries.get_user_posts;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserPostsQuery {
    UUID userId;
    int page;
    int size;
}
