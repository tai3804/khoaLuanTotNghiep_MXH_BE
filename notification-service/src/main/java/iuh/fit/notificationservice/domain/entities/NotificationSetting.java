package iuh.fit.notificationservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_settings", indexes = {
        @Index(name = "idx_notif_setting_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "like_post", nullable = false)
    @Builder.Default
    boolean likePost = true;

    @Column(name = "comment_post", nullable = false)
    @Builder.Default
    boolean commentPost = true;

    @Column(name = "share_post", nullable = false)
    @Builder.Default
    boolean sharePost = true;

    @Column(name = "friend_request", nullable = false)
    @Builder.Default
    boolean friendRequest = true;

    @Column(name = "message", nullable = false)
    @Builder.Default
    boolean message = true;

    @Column(name = "call", nullable = false)
    @Builder.Default
    boolean call = true;

    @Column(name = "system", nullable = false)
    @Builder.Default
    boolean system = true;

    @Column(name = "sound", nullable = false)
    @Builder.Default
    boolean sound = true;

    @Column(name = "email_notification", nullable = false)
    @Builder.Default
    boolean emailNotification = true;

    @Column(name = "updated_at")
    Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
