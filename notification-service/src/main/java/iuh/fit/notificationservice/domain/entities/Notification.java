package iuh.fit.notificationservice.domain.entities;

import iuh.fit.notificationservice.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient_id", columnList = "recipient_id"),
        @Index(name = "idx_notif_is_read", columnList = "is_read"),
        @Index(name = "idx_notif_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "recipient_id", nullable = false)
    UUID recipientId;

    @Column(name = "actor_id")
    UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    NotificationType type;

    @Column(name = "title", nullable = false)
    String title;

    @Column(name = "content", nullable = false, length = 1000)
    String content;

    @Column(name = "target_id")
    String targetId;

    @Column(name = "target_url")
    String targetUrl;

    @Column(name = "avatar_url")
    String avatarUrl;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    boolean isRead = false;

    @Column(name = "read_at")
    Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
