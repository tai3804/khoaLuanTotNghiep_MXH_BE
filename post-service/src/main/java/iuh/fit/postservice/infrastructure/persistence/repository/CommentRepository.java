package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends BaseJpaRepository<Comment, UUID> {
    Page<Comment> findByPostIdAndParentCommentIdIsNullAndDeletedFalse(UUID postId, Pageable pageable);
    Page<Comment> findByParentCommentIdAndDeletedFalse(UUID parentCommentId, Pageable pageable);
    List<Comment> findByParentCommentIdAndDeletedFalse(UUID parentCommentId);
    Optional<Comment> findByIdAndDeletedFalse(UUID id);
    long countByPostIdAndDeletedFalse(UUID postId);

    /** Thời điểm bình luận gần nhất của người dùng (kể cả bình luận đã xoá). */
    @Query("SELECT MAX(c.createdAt) FROM Comment c WHERE c.authorId = :authorId")
    java.time.LocalDateTime findLatestCreatedAtByAuthor(@Param("authorId") UUID authorId);

    long countByAuthorIdAndCreatedAtAfter(UUID authorId, java.time.LocalDateTime since);

    /** Số bình luận của mình dưới bài của từng tác giả kể từ mốc `since`. */
    @Query("SELECT p.authorId, COUNT(c) FROM Comment c, Post p WHERE c.postId = p.id AND c.authorId = :userId AND c.deleted = false AND c.createdAt >= :since GROUP BY p.authorId")
    List<Object[]> countInteractionsByAuthor(@Param("userId") UUID userId, @Param("since") java.time.LocalDateTime since);
}
