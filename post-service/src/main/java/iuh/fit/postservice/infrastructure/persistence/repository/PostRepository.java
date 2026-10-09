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
    Page<Post> findByDeletedFalse(Pageable pageable);
    Optional<Post> findByIdAndDeletedFalse(UUID id);
    List<Post> findByGroupIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(UUID groupId, PostStatus status);

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
    @Query(value = "UPDATE posts SET created_at = :createdAt WHERE id = :postId AND author_id = :authorId AND is_deleted = false", nativeQuery = true)
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
}
