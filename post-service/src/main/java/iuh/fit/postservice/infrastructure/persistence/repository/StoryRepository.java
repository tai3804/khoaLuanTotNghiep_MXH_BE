package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoryRepository extends BaseJpaRepository<Story, UUID> {

    List<Story> findByUserIdAndExpiresAtAfterAndIsDeletedFalseOrderByCreatedAtDesc(UUID userId, Instant now);

    Optional<Story> findByIdAndIsDeletedFalse(UUID id);

    @Query("""
        SELECT s FROM Story s
        WHERE s.expiresAt > :now
          AND s.isDeleted = false
          AND (s.userId = :userId OR s.userId IN :followingIds)
        ORDER BY s.createdAt DESC
    """)
    List<Story> findActiveFeedStories(
            @Param("userId") UUID userId,
            @Param("followingIds") List<UUID> followingIds,
            @Param("now") Instant now
    );

    @Query("""
        SELECT s FROM Story s
        WHERE s.expiresAt > :now
          AND s.isDeleted = false
        ORDER BY s.createdAt DESC
    """)
    List<Story> findAllActiveStories(@Param("now") Instant now);
}
