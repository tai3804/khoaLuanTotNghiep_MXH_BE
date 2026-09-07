package iuh.fit.moderationservice.application.features.report.commands.create_report;

import iuh.fit.moderationservice.domain.enums.ReportReason;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
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
public class CreateReportResult {
    UUID reportId;
    UUID reporterId;
    TargetType targetType;
    UUID targetId;
    ReportReason reason;
    String description;
    ReportStatus status;
    LocalDateTime createdAt;
}
