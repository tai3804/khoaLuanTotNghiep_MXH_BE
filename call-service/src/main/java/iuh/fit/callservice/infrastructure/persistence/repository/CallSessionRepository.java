package iuh.fit.callservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallSessionRepository extends BaseJpaRepository<CallSession, UUID> {

    @Query("""
        SELECT cs FROM CallSession cs
        JOIN CallParticipant cp ON cs.id = cp.callSessionId
        WHERE cp.userId = :userId AND cs.status IN ('INITIATED', 'ACTIVE') AND cp.status IN ('INVITED', 'RINGING', 'CONNECTED')
    """)
    Optional<CallSession> findActiveCallSessionByUserId(@Param("userId") UUID userId);
}
