package iuh.fit.moderationservice.application.features.report.commands.create_report;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.moderationservice.application.exception.ModerationServiceErrorCode;
import iuh.fit.moderationservice.application.mapper.ReportFeatureMapper;
import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ReportRepository;
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

    @Transactional
    public CreateReportResult handle(CreateReportCommand command) {
        boolean alreadyReported = reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                command.getReporterId(), command.getTargetType(), command.getTargetId(), ReportStatus.PENDING
        );

        if (alreadyReported) {
            throw new BusinessException(ModerationServiceErrorCode.REPORT_ALREADY_SUBMITTED);
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

        return reportFeatureMapper.toCreateReportResult(report);
    }
}
