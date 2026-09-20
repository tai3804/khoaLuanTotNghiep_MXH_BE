package iuh.fit.adminservice.domain.repository;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportStatus;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    Report save(Report report);
    Optional<Report> findById(Long id);
    List<Report> findAll();
    List<Report> findByStatus(ReportStatus status);
}
