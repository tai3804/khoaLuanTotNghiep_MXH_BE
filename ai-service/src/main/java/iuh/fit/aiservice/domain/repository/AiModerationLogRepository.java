package iuh.fit.aiservice.domain.repository;

import iuh.fit.aiservice.domain.entities.AiModerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiModerationLogRepository extends JpaRepository<AiModerationLog, UUID> {

    Page<AiModerationLog> findByOrderByCreatedAtDesc(Pageable pageable);

    Page<AiModerationLog> findByActionTaken(String actionTaken, Pageable pageable);

    @Query("SELECT COUNT(l) FROM AiModerationLog l WHERE l.actionTaken != 'ALLOW'")
    long countFlaggedContent();
}
