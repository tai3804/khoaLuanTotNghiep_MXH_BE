package iuh.fit.feedservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface UserFeedItemRepository extends BaseJpaRepository<UserFeedItem, UUID> {

    Page<UserFeedItem> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Transactional
    void deleteByPostId(UUID postId);

    @Modifying
    @Transactional
    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    @org.springframework.data.jpa.repository.Query("SELECT u.userId FROM UserFeedItem u WHERE u.postId = :postId AND u.userId IN :userIds")
    java.util.Set<UUID> findExistingUserIds(
            @org.springframework.data.repository.query.Param("postId") UUID postId,
            @org.springframework.data.repository.query.Param("userIds") java.util.Collection<UUID> userIds);
}
