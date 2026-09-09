package iuh.fit.chatservice.infrastructure.persistence.repository;

import iuh.fit.chatservice.domain.entities.MessageUserDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageUserDeletionRepository extends JpaRepository<MessageUserDeletion, UUID> {

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);

    @Query("SELECT d.messageId FROM MessageUserDeletion d WHERE d.userId = :userId AND d.messageId IN :messageIds")
    List<UUID> findDeletedMessageIdsByUser(@Param("userId") UUID userId, @Param("messageIds") List<UUID> messageIds);

    @Query("SELECT d.messageId FROM MessageUserDeletion d WHERE d.userId = :userId")
    List<UUID> findAllDeletedMessageIdsByUserId(@Param("userId") UUID userId);
}
