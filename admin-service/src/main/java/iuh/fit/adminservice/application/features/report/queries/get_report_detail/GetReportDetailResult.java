package iuh.fit.adminservice.application.features.report.queries.get_report_detail;

import iuh.fit.adminservice.domain.enums.ReportReason;
import iuh.fit.adminservice.domain.enums.ReportStatus;
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
public class GetReportDetailResult {
    UUID reportId;
    UUID reporterId;
    TargetType targetType;
    UUID targetId;
    ReportReason reason;
    String description;
    ReportStatus status;
    LocalDateTime createdAt;
    LocalDateTime resolvedAt;
    UUID resolvedBy;
}
