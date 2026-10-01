package iuh.fit.adminservice.application.features.report.commands.process_report;

import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.event.ModerationEventPublisher;
import iuh.fit.commonframework.application.exception.BusinessException;
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
                .orElseThrow(() -> new BusinessException(AdminServiceErrorCode.REPORT_NOT_FOUND));

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
