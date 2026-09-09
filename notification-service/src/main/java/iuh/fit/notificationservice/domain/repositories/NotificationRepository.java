package iuh.fit.notificationservice.domain.repositories;

import iuh.fit.notificationservice.domain.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
