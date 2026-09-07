package iuh.fit.chatservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.chatservice.domain.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends BaseJpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndDeletedFalseOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findTopByConversationIdAndDeletedFalseOrderByCreatedAtDesc(UUID conversationId);

    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.conversationId = :conversationId
          AND m.deleted = false
          AND (:lastReadId IS NULL OR m.createdAt > (SELECT lm.createdAt FROM Message lm WHERE lm.id = :lastReadId))
    """)
    long countUnreadMessages(@Param("conversationId") UUID conversationId, @Param("lastReadId") UUID lastReadId);
}
