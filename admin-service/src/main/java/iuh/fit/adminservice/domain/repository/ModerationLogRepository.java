package iuh.fit.adminservice.domain.repository;

import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModerationLogRepository extends BaseJpaRepository<ModerationLog, UUID> {

    Page<ModerationLog> findByModeratorId(UUID moderatorId, Pageable pageable);

    Page<ModerationLog> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId, Pageable pageable);
}
