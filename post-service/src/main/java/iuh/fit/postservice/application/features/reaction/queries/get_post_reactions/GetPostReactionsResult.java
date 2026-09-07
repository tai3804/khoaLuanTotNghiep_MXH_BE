package iuh.fit.postservice.application.features.reaction.queries.get_post_reactions;

import iuh.fit.postservice.domain.enums.ReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPostReactionsResult {
    UUID id;
    UUID postId;
    UUID userId;
    ReactionType type;
    LocalDateTime createdAt;
}
