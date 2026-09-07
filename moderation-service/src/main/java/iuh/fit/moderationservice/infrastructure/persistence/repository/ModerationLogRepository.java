package iuh.fit.moderationservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.moderationservice.domain.entities.ModerationLog;
import iuh.fit.moderationservice.domain.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModerationLogRepository extends BaseJpaRepository<ModerationLog, UUID> {

    Page<ModerationLog> findByModeratorId(UUID moderatorId, Pageable pageable);

    Page<ModerationLog> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId, Pageable pageable);
}
