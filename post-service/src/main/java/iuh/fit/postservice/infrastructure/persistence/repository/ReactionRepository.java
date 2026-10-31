package iuh.fit.postservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.enums.ReactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface ReactionRepository extends BaseJpaRepository<Reaction, UUID> {
    Optional<Reaction> findByPostIdAndUserId(UUID postId, UUID userId);
    Page<Reaction> findByPostId(UUID postId, Pageable pageable);
    long countByPostId(UUID postId);

    @Query("SELECT r.type, COUNT(r) FROM Reaction r WHERE r.postId = :postId GROUP BY r.type")
    List<Object[]> rawCountGroupByReactionType(@Param("postId") UUID postId);

    default Map<ReactionType, Long> countGroupByReactionType(UUID postId) {
        return rawCountGroupByReactionType(postId).stream()
                .collect(Collectors.toMap(
                        res -> (ReactionType) res[0],
                        res -> (Long) res[1],
                        (existing, replacement) -> existing,
                        () -> new EnumMap<>(ReactionType.class)
                ));
    }

    @Modifying
    @Transactional
    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    /** Số lượt mình thả cảm xúc cho bài của từng tác giả kể từ mốc `since` (dùng tính độ thân thiết khi xếp hạng bảng tin). */
    @Query("SELECT p.authorId, COUNT(r) FROM Reaction r, Post p WHERE r.postId = p.id AND r.userId = :userId AND r.createdAt >= :since GROUP BY p.authorId")
    List<Object[]> countInteractionsByAuthor(@Param("userId") UUID userId, @Param("since") java.time.LocalDateTime since);
}
