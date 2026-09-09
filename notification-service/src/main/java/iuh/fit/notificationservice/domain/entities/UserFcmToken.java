package iuh.fit.notificationservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_fcm_tokens", indexes = {
        @Index(name = "idx_fcm_user_id", columnList = "user_id"),
        @Index(name = "idx_fcm_token", columnList = "fcm_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserFcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "fcm_token", nullable = false, length = 512)
    String fcmToken;

    @Column(name = "device_type", length = 50)
    String deviceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at")
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
