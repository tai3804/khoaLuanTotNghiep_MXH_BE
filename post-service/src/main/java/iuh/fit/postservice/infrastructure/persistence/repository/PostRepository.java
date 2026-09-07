package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends BaseJpaRepository<Post, UUID> {
    Page<Post> findByAuthorIdAndDeletedFalse(UUID authorId, Pageable pageable);
    Page<Post> findByDeletedFalse(Pageable pageable);
    Optional<Post> findByIdAndDeletedFalse(UUID id);
}
