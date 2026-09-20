package iuh.fit.adminservice.infrastructure.persistence;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final JpaReportRepository jpaReportRepository;

    @Override
    public Report save(Report report) {
        return jpaReportRepository.save(report);
    }

    @Override
    public Optional<Report> findById(Long id) {
        return jpaReportRepository.findById(id);
    }

    @Override
    public List<Report> findAll() {
        return jpaReportRepository.findAll();
    }

    @Override
    public List<Report> findByStatus(ReportStatus status) {
        return jpaReportRepository.findByStatus(status);
    }
}
