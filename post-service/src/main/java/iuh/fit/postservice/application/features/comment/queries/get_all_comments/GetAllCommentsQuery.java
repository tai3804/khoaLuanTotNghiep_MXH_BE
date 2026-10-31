package iuh.fit.postservice.application.features.comment.queries.get_all_comments;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetAllCommentsQuery {
    UUID postId;
    UUID viewerId;
    BaseFilter filter;
}
