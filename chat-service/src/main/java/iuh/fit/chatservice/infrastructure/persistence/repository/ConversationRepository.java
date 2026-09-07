package iuh.fit.chatservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.enums.ConversationType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends BaseJpaRepository<Conversation, UUID> {

    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationMember cm1 ON c.id = cm1.conversationId
        JOIN ConversationMember cm2 ON c.id = cm2.conversationId
        WHERE c.type = :type
          AND cm1.userId = :userId1 AND cm1.status = 'ACTIVE'
          AND cm2.userId = :userId2 AND cm2.status = 'ACTIVE'
    """)
    Optional<Conversation> findDirectConversationBetweenUsers(
            @Param("type") ConversationType type,
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2
    );
}
