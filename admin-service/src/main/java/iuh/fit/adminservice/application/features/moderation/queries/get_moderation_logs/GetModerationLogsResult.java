package iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs;

import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.TargetType;
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
