package iuh.fit.chatservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_user_deletions", indexes = {
        @Index(name = "idx_mud_user_msg", columnList = "user_id, message_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageUserDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "message_id", nullable = false)
    UUID messageId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        deletedAt = Instant.now();
    }
}
