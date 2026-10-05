package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface PostRepository extends BaseJpaRepository<Post, UUID> {
    Page<Post> findByAuthorIdAndDeletedFalse(UUID authorId, Pageable pageable);
    Page<Post> findByAuthorIdAndDeletedFalseAndIsArchivedFalse(UUID authorId, Pageable pageable);
    Page<Post> findByAuthorIdAndDeletedFalseAndIsArchivedTrue(UUID authorId, Pageable pageable);
    List<Post> findByGroupIdAndAppealStatusOrderByUpdatedAtDesc(UUID groupId, iuh.fit.postservice.domain.enums.ModerationAppealStatus appealStatus);
    Page<Post> findByDeletedFalse(Pageable pageable);
    Optional<Post> findByIdAndDeletedFalse(UUID id);
    List<Post> findByGroupIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(UUID groupId, iuh.fit.postservice.domain.enums.PostStatus status);
    List<Post> findByAppealStatusOrderByUpdatedAtDesc(iuh.fit.postservice.domain.enums.ModerationAppealStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Post p SET p.privacy = :privacy WHERE p.authorId = :authorId AND p.deleted = false")
    int updatePrivacyByAuthorId(
            @org.springframework.data.repository.query.Param("authorId") UUID authorId,
            @org.springframework.data.repository.query.Param("privacy") iuh.fit.postservice.domain.enums.PostPrivacy privacy);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(value = "UPDATE posts SET created_at = :createdAt WHERE id = :postId AND author_id = :authorId AND is_deleted = false", nativeQuery = true)
    int updateCreatedAtByIdAndAuthorId(
            @org.springframework.data.repository.query.Param("postId") UUID postId,
            @org.springframework.data.repository.query.Param("authorId") UUID authorId,
            @org.springframework.data.repository.query.Param("createdAt") LocalDateTime createdAt);
}
