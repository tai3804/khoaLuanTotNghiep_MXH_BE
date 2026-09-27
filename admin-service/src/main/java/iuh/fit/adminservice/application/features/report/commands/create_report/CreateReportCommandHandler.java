package iuh.fit.adminservice.application.features.report.commands.create_report;

import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.application.mapper.ReportFeatureMapper;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.event.ModerationEventPublisher;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.ReportCreatedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateReportCommandHandler {

    ReportRepository reportRepository;
    ReportFeatureMapper reportFeatureMapper;
    ModerationEventPublisher moderationEventPublisher;

    @Transactional
    public CreateReportResult handle(CreateReportCommand command) {
        boolean alreadyReported = reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                command.getReporterId(), command.getTargetType(), command.getTargetId(), ReportStatus.PENDING
        );

        if (alreadyReported) {
            throw new BusinessException(AdminServiceErrorCode.REPORT_ALREADY_SUBMITTED);
        }

        Report report = Report.builder()
                .reporterId(command.getReporterId())
                .targetType(command.getTargetType())
                .targetId(command.getTargetId())
                .reason(command.getReason())
                .description(command.getDescription())
                .status(ReportStatus.PENDING)
                .build();

        report = reportRepository.save(report);

        moderationEventPublisher.publishReportCreated(
                ReportCreatedEvent.builder()
                        .reportId(report.getId())
                        .targetId(report.getTargetId())
                        .targetType(report.getTargetType().name())
                        .reporterId(report.getReporterId())
                        .reason(report.getReason().name())
                        .build()
        );

        return reportFeatureMapper.toCreateReportResult(report);
    }
}
