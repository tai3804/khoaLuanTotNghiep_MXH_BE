package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.PostView;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PostViewRepository extends BaseJpaRepository<PostView, UUID> {

    /** Ghi một lượt xem; xem lại cùng bài thì giữ nguyên lần xem đầu (không lỗi trùng khoá khi nhiều request song song). */
    @Modifying
    @Query(value = "INSERT INTO post_views (id, user_id, post_id, seen_at) VALUES (:id, :userId, :postId, :seenAt) "
            + "ON CONFLICT (user_id, post_id) DO NOTHING", nativeQuery = true)
    int insertIgnore(@Param("id") UUID id, @Param("userId") UUID userId, @Param("postId") UUID postId,
                     @Param("seenAt") LocalDateTime seenAt);

    /**
     * Ghi nhận cả lô bài đã xem bằng MỘT câu INSERT ... SELECT: chỉ bài còn tồn tại mới được ghi, xem lại thì giữ lần đầu.
     * Trả về số dòng thực sự được thêm mới.
     */
    @Modifying
    @Query(value = "INSERT INTO post_views (id, user_id, post_id, seen_at) "
            + "SELECT gen_random_uuid(), :userId, p.id, :seenAt FROM posts p WHERE p.id IN (:postIds) AND p.is_deleted = false "
            + "ON CONFLICT (user_id, post_id) DO NOTHING", nativeQuery = true)
    int insertIgnoreBatch(@Param("userId") UUID userId, @Param("postIds") java.util.Collection<UUID> postIds,
                          @Param("seenAt") LocalDateTime seenAt);

    /** Trong số `postIds`, những bài người dùng đã xem từ mốc `since` trở lại đây. */
    @Query("SELECT v.postId FROM PostView v WHERE v.userId = :userId AND v.postId IN :postIds AND v.seenAt >= :since")
    List<UUID> findSeenPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds,
                               @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM PostView v WHERE v.userId = :userId AND v.seenAt < :cutoff")
    int deleteOlderThan(@Param("userId") UUID userId, @Param("cutoff") LocalDateTime cutoff);
}
