package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.ProfileViewHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileViewHistoryRepository extends JpaRepository<ProfileViewHistory, UUID> {
    Optional<ProfileViewHistory> findByTargetUserIdAndViewerId(UUID targetUserId, UUID viewerId);

    Page<ProfileViewHistory> findByTargetUserIdOrderByLastViewedAtDesc(UUID targetUserId, Pageable pageable);

    long countByTargetUserId(UUID targetUserId);

    java.util.List<ProfileViewHistory> findByTargetUserId(UUID targetUserId);
}
