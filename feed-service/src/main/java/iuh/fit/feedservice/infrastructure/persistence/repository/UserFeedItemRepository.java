package iuh.fit.feedservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserFeedItemRepository extends BaseJpaRepository<UserFeedItem, UUID> {

    Page<UserFeedItem> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    void deleteByPostId(UUID postId);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
}
