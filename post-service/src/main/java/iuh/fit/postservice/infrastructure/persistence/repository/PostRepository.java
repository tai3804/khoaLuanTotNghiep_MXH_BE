package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface PostRepository extends BaseJpaRepository<Post, UUID> {
    Page<Post> findByAuthorIdAndDeletedFalse(UUID authorId, Pageable pageable);
    Page<Post> findByAuthorIdAndDeletedFalseAndIsArchivedFalse(UUID authorId, Pageable pageable);
    Page<Post> findByAuthorIdAndDeletedFalseAndIsArchivedTrue(UUID authorId, Pageable pageable);
    List<Post> findByGroupIdAndAppealStatusOrderByUpdatedAtDesc(UUID groupId, iuh.fit.postservice.domain.enums.ModerationAppealStatus appealStatus);
    Page<Post> findByDeletedFalse(Pageable pageable);
    Optional<Post> findByIdAndDeletedFalse(UUID id);
    List<Post> findByGroupIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(UUID groupId, PostStatus status);

    /** Bài của một người trong nhóm đã bị quản trị viên gỡ (để họ xem và kháng nghị). */
    List<Post> findByGroupIdAndAuthorIdAndDeletedTrueAndModerationActionIsNotNullOrderByUpdatedAtDesc(UUID groupId, UUID authorId);

    /** Số bài đang chờ duyệt trong nhóm (huy hiệu ở thanh tab của quản trị viên). */
    long countByGroupIdAndStatusAndDeletedFalse(UUID groupId, PostStatus status);

    /** Số bài đã đăng trong nhóm kể từ một mốc (thẻ "Hoạt động của nhóm"). */
    long countByGroupIdAndStatusAndDeletedFalseAndCreatedAtAfter(UUID groupId, PostStatus status, LocalDateTime since);

    /** Số kháng nghị đang chờ quản trị viên nhóm xử lý. */
    long countByGroupIdAndAppealStatus(UUID groupId, iuh.fit.postservice.domain.enums.ModerationAppealStatus appealStatus);

    /** Số bài đang được ghim trong nhóm (giới hạn số bài ghim). */
    long countByGroupIdAndGroupPinnedAtIsNotNullAndDeletedFalse(UUID groupId);

    /** Bài đã đăng của các nhóm cho trước (bảng tin "Nhóm của bạn"). */
    Page<Post> findByGroupIdInAndStatusAndDeletedFalseAndIsArchivedFalse(java.util.Collection<UUID> groupIds, PostStatus status, Pageable pageable);

    /** Gỡ bài viết của một thành viên trong nhóm khi họ bị xóa kèm "xóa nội dung". */
    @Modifying
    @Query("UPDATE Post p SET p.deleted = true, p.groupPinnedAt = null WHERE p.groupId = :groupId AND p.authorId = :authorId AND p.deleted = false")
    int softDeleteByGroupIdAndAuthorId(@Param("groupId") UUID groupId, @Param("authorId") UUID authorId);

    /** Ảnh/video của các bài đã đăng trong nhóm, bài mới nhất trước (tab "Ảnh & video"). */
    @Query(value = "SELECT new iuh.fit.postservice.application.features.post.queries.group_media.GroupMediaItem(m.id, m.postId, m.fileUrl, m.mediaType, p.authorId, p.createdAt) "
            + "FROM PostMedia m, Post p WHERE p.id = m.postId AND p.groupId = :groupId AND p.deleted = false AND p.status = 'PUBLISHED' "
            + "AND p.isArchived = false AND m.deleted = false AND m.mediaType IN :types ORDER BY p.createdAt DESC, m.sortOrder ASC",
            countQuery = "SELECT COUNT(m) FROM PostMedia m, Post p WHERE p.id = m.postId AND p.groupId = :groupId AND p.deleted = false "
                    + "AND p.status = 'PUBLISHED' AND p.isArchived = false AND m.deleted = false AND m.mediaType IN :types")
    Page<iuh.fit.postservice.application.features.post.queries.group_media.GroupMediaItem> findGroupMedia(
            @Param("groupId") UUID groupId,
            @Param("types") java.util.Collection<iuh.fit.postservice.domain.enums.MediaType> types,
            Pageable pageable);

    /** Bài chưa có mốc hoạt động (dữ liệu cũ) lấy mốc là lúc đăng, để sắp xếp theo hoạt động không bị lệch. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE posts SET last_activity_at = created_at WHERE last_activity_at IS NULL", nativeQuery = true)
    int backfillLastActivity();

    /** Bài của chính tác giả trong nhóm đang chờ duyệt (để họ thấy bài của mình chưa được hiển thị). */
    List<Post> findByGroupIdAndAuthorIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(UUID groupId, UUID authorId, PostStatus status);

    /** Nhóm bị xóa thì mọi bài của nhóm cũng bị gỡ (không còn nơi nào để hiển thị). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Post p SET p.deleted = true WHERE p.groupId = :groupId AND p.deleted = false")
    int softDeleteByGroupId(@Param("groupId") UUID groupId);
    List<Post> findByAppealStatusOrderByUpdatedAtDesc(iuh.fit.postservice.domain.enums.ModerationAppealStatus status);

    @Query("SELECT p FROM Post p JOIN p.hashtags h WHERE LOWER(h) = LOWER(:hashtag) AND p.deleted = false AND p.status = 'PUBLISHED' ORDER BY p.createdAt DESC")
    Page<Post> findByHashtag(@Param("hashtag") String hashtag, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.privacy = :privacy WHERE p.authorId = :authorId AND p.deleted = false")
    int updatePrivacyByAuthorId(
            @Param("authorId") UUID authorId,
            @Param("privacy") PostPrivacy privacy);

    @Modifying
    @Transactional
    @Query(value = "UPDATE posts SET created_at = :createdAt, last_activity_at = :createdAt WHERE id = :postId AND author_id = :authorId AND is_deleted = false", nativeQuery = true)
    int updateCreatedAtByIdAndAuthorId(
            @Param("postId") UUID postId,
            @Param("authorId") UUID authorId,
            @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId AND p.deleted = false")
    int incrementViewCount(@Param("postId") UUID postId);

    List<Post> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(UUID authorId);

    List<Post> findByStatusAndScheduledPublishAtLessThanEqualAndDeletedFalse(PostStatus status, java.time.Instant now);

    Page<Post> findByAuthorIdAndStatusAndDeletedFalseOrderByScheduledPublishAtAsc(UUID authorId, PostStatus status, Pageable pageable);

    /** Tập bài viết ứng viên để xếp hạng bảng tin: bài đã đăng, chưa xoá, chưa ẩn, mới nhất trước. */
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'PUBLISHED' AND p.isArchived = false AND p.createdAt >= :cutoff ORDER BY p.createdAt DESC")
    List<Post> findRankingCandidates(@Param("cutoff") java.time.LocalDateTime cutoff, org.springframework.data.domain.Pageable pageable);

    /** Thời điểm đăng bài gần nhất của tác giả (kể cả bài đã xoá, để không lách giãn cách bằng cách xoá bài). */
    @Query("SELECT MAX(p.createdAt) FROM Post p WHERE p.authorId = :authorId")
    java.time.LocalDateTime findLatestCreatedAtByAuthor(@Param("authorId") UUID authorId);

    long countByAuthorIdAndCreatedAtAfter(UUID authorId, java.time.LocalDateTime since);

    /** Id của những bài còn tồn tại (chưa xoá) trong `ids`, không nạp cả entity. */
    @Query("SELECT p.id FROM Post p WHERE p.id IN :ids AND p.deleted = false")
    List<UUID> findExistingIds(@Param("ids") java.util.Collection<UUID> ids);

    /** Số lần mình chia sẻ bài của từng tác giả gốc kể từ mốc `since`. */
    @Query("SELECT o.authorId, COUNT(s) FROM Post s, Post o WHERE s.originalPostId = o.id AND s.authorId = :userId AND s.deleted = false AND s.createdAt >= :since GROUP BY o.authorId")
    List<Object[]> countSharesByOriginalAuthor(@Param("userId") UUID userId, @Param("since") java.time.LocalDateTime since);
}
