package iuh.fit.adminservice.application.features.moderation.commands.moderate_post;

import iuh.fit.adminservice.domain.enums.ModerationAction;
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
