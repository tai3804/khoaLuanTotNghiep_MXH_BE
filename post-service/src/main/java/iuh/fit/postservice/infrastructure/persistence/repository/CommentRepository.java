package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends BaseJpaRepository<Comment, UUID> {
    Page<Comment> findByPostIdAndParentCommentIdIsNullAndDeletedFalse(UUID postId, Pageable pageable);
    Page<Comment> findByParentCommentIdAndDeletedFalse(UUID parentCommentId, Pageable pageable);
    Optional<Comment> findByIdAndDeletedFalse(UUID id);
    long countByPostIdAndDeletedFalse(UUID postId);
}
