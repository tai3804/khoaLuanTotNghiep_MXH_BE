package iuh.fit.moderationservice.application.features.report.commands.process_report;

import iuh.fit.moderationservice.domain.enums.ModerationAction;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessReportCommand {
    UUID reportId;
    UUID moderatorId;
    ModerationAction action;
    String note;
}
