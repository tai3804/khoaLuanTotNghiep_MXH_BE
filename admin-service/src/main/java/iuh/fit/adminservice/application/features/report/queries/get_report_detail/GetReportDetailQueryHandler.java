package iuh.fit.adminservice.application.features.report.queries.get_report_detail;

import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.application.mapper.ReportFeatureMapper;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.commonframework.application.exception.BusinessException;
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
                .orElseThrow(() -> new BusinessException(AdminServiceErrorCode.REPORT_NOT_FOUND));

        return reportFeatureMapper.toGetReportDetailResult(report);
    }
}
