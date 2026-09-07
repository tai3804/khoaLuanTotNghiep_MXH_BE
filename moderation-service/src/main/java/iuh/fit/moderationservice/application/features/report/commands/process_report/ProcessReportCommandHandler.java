package iuh.fit.moderationservice.application.features.report.commands.process_report;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.moderationservice.application.exception.ModerationServiceErrorCode;
import iuh.fit.moderationservice.domain.entities.ModerationLog;
import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.domain.enums.ModerationAction;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
import iuh.fit.moderationservice.domain.enums.TargetType;
import iuh.fit.moderationservice.infrastructure.event.ModerationEventPublisher;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ModerationLogRepository;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ReportRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessReportCommandHandler {

    ReportRepository reportRepository;
    ModerationLogRepository moderationLogRepository;
    ModerationEventPublisher moderationEventPublisher;

    @Transactional
    public void handle(ProcessReportCommand command) {
        Report report = reportRepository.findById(command.getReportId())
                .orElseThrow(() -> new BusinessException(ModerationServiceErrorCode.REPORT_NOT_FOUND));

        if (command.getAction() == ModerationAction.DISMISS) {
            report.setStatus(ReportStatus.DISMISSED);
        } else {
            report.setStatus(ReportStatus.RESOLVED);
        }
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedBy(command.getModeratorId());
        reportRepository.save(report);

        // Record moderation log
        ModerationLog log = ModerationLog.builder()
                .moderatorId(command.getModeratorId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .action(command.getAction())
                .reason(report.getReason().name())
                .note(command.getNote())
                .reportId(report.getId())
                .build();
        moderationLogRepository.save(log);

        // If DELETE_POST action, publish event to Kafka
        if (command.getAction() == ModerationAction.DELETE_POST && report.getTargetType() == TargetType.POST) {
            moderationEventPublisher.publishPostModerated(
                    report.getTargetId(),
                    command.getAction().name(),
                    report.getReason().name(),
                    command.getModeratorId()
            );
        }
    }
}
