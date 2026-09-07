package iuh.fit.moderationservice.application.features.report.queries.get_report_detail;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetReportDetailQuery {
    UUID reportId;
}
