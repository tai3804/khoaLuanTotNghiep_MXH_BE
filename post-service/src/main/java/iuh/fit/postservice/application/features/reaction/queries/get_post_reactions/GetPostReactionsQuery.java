package iuh.fit.postservice.application.features.reaction.queries.get_post_reactions;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPostReactionsQuery {
    UUID postId;
    int page;
    int size;
}
