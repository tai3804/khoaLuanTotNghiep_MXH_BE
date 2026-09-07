package iuh.fit.postservice.application.features.reaction.commands.toggle_reaction;

import iuh.fit.postservice.domain.enums.ReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleReactionCommand {
    UUID postId;
    UUID userId;
    ReactionType type;
}
