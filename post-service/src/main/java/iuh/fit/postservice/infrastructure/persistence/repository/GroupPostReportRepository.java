package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.GroupPostReport;
import iuh.fit.postservice.domain.enums.GroupReportStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupPostReportRepository extends BaseJpaRepository<GroupPostReport, UUID> {

    Optional<GroupPostReport> findByPostIdAndReporterId(UUID postId, UUID reporterId);

    List<GroupPostReport> findByGroupIdAndStatusOrderByCreatedAtAsc(UUID groupId, GroupReportStatus status);

    boolean existsByPostIdAndStatus(UUID postId, GroupReportStatus status);

    /** Số bài viết (không phải số báo cáo) đang có báo cáo chờ xử lý trong nhóm. */
    @Query("SELECT COUNT(DISTINCT r.postId) FROM GroupPostReport r WHERE r.groupId = :groupId AND r.status = :status")
    long countReportedPosts(@Param("groupId") UUID groupId, @Param("status") GroupReportStatus status);

    /** Đóng mọi báo cáo đang chờ của một bài viết khi quản trị viên đã quyết định. */
    @Modifying
    @Transactional
    @Query("UPDATE GroupPostReport r SET r.status = :to, r.handledBy = :by, r.handledAt = :at WHERE r.postId = :postId AND r.status = :from")
    int resolveOpenReports(@Param("postId") UUID postId, @Param("from") GroupReportStatus from, @Param("to") GroupReportStatus to,
                           @Param("by") UUID by, @Param("at") LocalDateTime at);
}
