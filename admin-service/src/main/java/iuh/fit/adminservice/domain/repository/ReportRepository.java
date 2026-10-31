package iuh.fit.adminservice.domain.repository;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportSource;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends BaseJpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, TargetType targetType, UUID targetId, ReportStatus status);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(UUID reporterId, TargetType targetType, UUID targetId, Collection<ReportStatus> statuses);

    boolean existsByTargetTypeAndTargetIdAndSourceAndStatusIn(TargetType targetType, UUID targetId, ReportSource source, Collection<ReportStatus> statuses);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findByStatusIn(Collection<ReportStatus> statuses, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId, Pageable pageable);

    List<Report> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);

    boolean existsByTargetTypeAndTargetIdAndStatusInAndIdNot(TargetType targetType, UUID targetId, Collection<ReportStatus> statuses, UUID excludedReportId);

    long countByStatus(ReportStatus status);

    long countByTargetTypeAndTargetIdAndStatus(TargetType targetType, UUID targetId, ReportStatus status);

    long countByStatusInAndResolvedAtBetween(Collection<ReportStatus> statuses, LocalDateTime start, LocalDateTime end);
}
