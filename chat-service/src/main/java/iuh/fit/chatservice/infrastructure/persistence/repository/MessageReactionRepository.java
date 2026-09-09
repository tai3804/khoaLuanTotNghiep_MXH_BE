package iuh.fit.chatservice.infrastructure.persistence.repository;

import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import iuh.fit.chatservice.domain.entities.MessageReaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageReactionRepository extends BaseJpaRepository<MessageReaction, UUID> {

    Optional<MessageReaction> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<MessageReaction> findByMessageId(UUID messageId);

    List<MessageReaction> findByMessageIdIn(List<UUID> messageIds);

    void deleteByMessageIdAndUserId(UUID messageId, UUID userId);
}
