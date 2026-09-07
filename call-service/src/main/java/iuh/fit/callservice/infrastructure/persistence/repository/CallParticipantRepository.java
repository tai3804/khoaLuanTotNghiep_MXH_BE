package iuh.fit.callservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallParticipantRepository extends BaseJpaRepository<CallParticipant, UUID> {

    Optional<CallParticipant> findByCallSessionIdAndUserId(UUID callSessionId, UUID userId);

    List<CallParticipant> findByCallSessionId(UUID callSessionId);

    List<CallParticipant> findByCallSessionIdAndStatus(UUID callSessionId, ParticipantStatus status);

    long countByCallSessionIdAndStatus(UUID callSessionId, ParticipantStatus status);

    @Query("""
        SELECT cp FROM CallParticipant cp
        WHERE cp.userId = :userId
        ORDER BY cp.createdAt DESC
    """)
    Page<CallParticipant> findUserCallHistory(@Param("userId") UUID userId, Pageable pageable);
}
