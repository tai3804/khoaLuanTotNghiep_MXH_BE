package iuh.fit.postservice.application.features.saved_post.queries.get_saved_posts;

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
public class GetSavedPostsQuery {
    UUID userId;
    String collectionName;
    BaseFilter filter;
}
