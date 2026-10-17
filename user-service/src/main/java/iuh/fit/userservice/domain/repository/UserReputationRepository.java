package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, UUID> {
    Optional<UserReputation> findByUserId(UUID userId);

    List<UserReputation> findByUserIdIn(Collection<UUID> userIds);

    /** Người chưa đạt điểm tối đa, không bị khóa, và đã quá `cutoff` kể từ mốc hồi điểm gần nhất. */
    @Query("SELECT r FROM UserReputation r WHERE r.lockedUntil IS NULL AND r.score < :maxScore "
            + "AND COALESCE(r.recoveryAnchorAt, r.updatedAt) <= :cutoff")
    List<UserReputation> findRecoverable(@Param("maxScore") int maxScore, @Param("cutoff") LocalDateTime cutoff);

    /** Các khoản khóa đã hết hạn, cần mở khóa. */
    @Query("SELECT r FROM UserReputation r WHERE r.lockedUntil IS NOT NULL AND r.lockedUntil <= :now")
    List<UserReputation> findExpiredLocks(@Param("now") LocalDateTime now);
}
