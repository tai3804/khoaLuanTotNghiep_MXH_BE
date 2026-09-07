package iuh.fit.postservice.application.features.comment.queries.get_post_comments;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPostCommentsQuery {
    UUID postId;
    UUID parentCommentId;
    int page;
    int size;
}
