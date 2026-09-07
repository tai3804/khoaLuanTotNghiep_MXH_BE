package iuh.fit.moderationservice.application.features.report.queries.get_report_detail;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.moderationservice.application.exception.ModerationServiceErrorCode;
import iuh.fit.moderationservice.application.mapper.ReportFeatureMapper;
import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ReportRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetReportDetailQueryHandler {

    ReportRepository reportRepository;
    ReportFeatureMapper reportFeatureMapper;

    @Transactional(readOnly = true)
    public GetReportDetailResult handle(GetReportDetailQuery query) {
        Report report = reportRepository.findById(query.getReportId())
                .orElseThrow(() -> new BusinessException(ModerationServiceErrorCode.REPORT_NOT_FOUND));

        return reportFeatureMapper.toGetReportDetailResult(report);
    }
}
