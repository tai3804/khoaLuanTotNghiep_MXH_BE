package iuh.fit.moderationservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
import iuh.fit.moderationservice.domain.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends BaseJpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, TargetType targetType, UUID targetId, ReportStatus status);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId, Pageable pageable);
}
