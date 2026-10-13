package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.ReputationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReputationEventRepository extends JpaRepository<ReputationEvent, UUID> {

    /** Các khoản trừ điểm của một nguồn (bài viết) chưa được hoàn lại. */
    List<ReputationEvent> findByUserIdAndSourceTypeAndSourceIdAndTypeAndRevertedFalse(
            UUID userId, String sourceType, UUID sourceId, String type);

    Page<ReputationEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
