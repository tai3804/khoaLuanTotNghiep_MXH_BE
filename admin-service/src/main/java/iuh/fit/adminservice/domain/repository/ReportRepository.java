package iuh.fit.adminservice.domain.repository;

import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends BaseJpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, TargetType targetType, UUID targetId, ReportStatus status);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId, Pageable pageable);

    long countByStatus(ReportStatus status);
}
