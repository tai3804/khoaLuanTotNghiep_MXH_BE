package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.SavedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPostRepository extends BaseJpaRepository<SavedPost, UUID> {

    Optional<SavedPost> findByUserIdAndPostId(UUID userId, UUID postId);

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    Page<SavedPost> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SavedPost> findByUserIdAndCollectionNameOrderByCreatedAtDesc(UUID userId, String collectionName, Pageable pageable);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
