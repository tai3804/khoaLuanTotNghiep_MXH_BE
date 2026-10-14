package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoryRepository extends BaseJpaRepository<Story, UUID> {

    List<Story> findByUserIdAndExpiresAtAfterAndIsDeletedFalseAndIsArchivedFalseOrderByCreatedAtDesc(UUID userId, Instant now);

    Optional<Story> findByIdAndIsDeletedFalse(UUID id);

    @Query("""
        SELECT s FROM Story s
        WHERE s.expiresAt > :now
          AND s.isDeleted = false
          AND s.isArchived = false
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
          AND s.isArchived = false
        ORDER BY s.createdAt DESC
    """)
    List<Story> findAllActiveStories(@Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Story s
        SET s.isArchived = true
        WHERE s.expiresAt <= :now
          AND s.isArchived = false
          AND s.isDeleted = false
    """)
    int archiveExpiredStories(@Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Story s
        SET s.isArchived = true, s.expiresAt = s.createdAt
        WHERE s.expiresAt IS NULL
          AND s.isArchived = false
    """)
    int archiveNullExpiryStories();

    @Query("""
        SELECT s FROM Story s
        WHERE s.userId = :userId
          AND (s.isArchived = true OR s.expiresAt <= :now)
          AND s.isDeleted = false
        ORDER BY s.createdAt DESC
    """)
    Page<Story> findArchivedStoriesByUserId(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            Pageable pageable
    );
}
