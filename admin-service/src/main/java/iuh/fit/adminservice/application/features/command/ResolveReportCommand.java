package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.ReportType;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.feign.PostFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResolveReportCommand {
    private final ReportRepository reportRepository;
    private final PostFeignClient postFeignClient;
    private final UserFeignClient userFeignClient;

    public void execute(Long reportId, boolean deleteTarget) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if (deleteTarget) {
            if (report.getTargetType() == ReportType.POST) {
                try {
                    postFeignClient.deletePost(report.getTargetId());
                } catch(Exception e) {
                    // Ignore or log error
                }
            } else if (report.getTargetType() == ReportType.USER) {
                try {
                    userFeignClient.banUser(report.getTargetId());
                } catch(Exception e) {
                    // Ignore
                }
            }
            report.setStatus(ReportStatus.RESOLVED);
        } else {
            report.setStatus(ReportStatus.REJECTED); // Dismissed
        }
        
        reportRepository.save(report);
    }
}
