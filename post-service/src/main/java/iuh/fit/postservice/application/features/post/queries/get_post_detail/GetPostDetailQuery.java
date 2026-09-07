package iuh.fit.postservice.application.features.post.queries.get_post_detail;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPostDetailQuery {
    UUID postId;
}
