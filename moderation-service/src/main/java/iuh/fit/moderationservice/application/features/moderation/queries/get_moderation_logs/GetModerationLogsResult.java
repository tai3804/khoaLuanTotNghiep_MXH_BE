package iuh.fit.moderationservice.application.features.moderation.queries.get_moderation_logs;

import iuh.fit.moderationservice.domain.enums.ModerationAction;
import iuh.fit.moderationservice.domain.enums.TargetType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModerationLogsResult {
    UUID logId;
    UUID moderatorId;
    TargetType targetType;
    UUID targetId;
    ModerationAction action;
    String reason;
    String note;
    UUID reportId;
    LocalDateTime createdAt;
}
