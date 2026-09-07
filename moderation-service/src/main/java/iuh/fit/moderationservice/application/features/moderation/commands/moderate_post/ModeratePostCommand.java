package iuh.fit.moderationservice.application.features.moderation.commands.moderate_post;

import iuh.fit.moderationservice.domain.enums.ModerationAction;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModeratePostCommand {
    UUID postId;
    UUID moderatorId;
    ModerationAction action;
    String reason;
    String note;
}
