package iuh.fit.postservice.domain.entities;

import iuh.fit.postservice.domain.enums.PostPrivacy;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "stories", indexes = {
        @Index(name = "idx_story_user_id", columnList = "user_id"),
        @Index(name = "idx_story_expires_at", columnList = "expires_at"),
        @Index(name = "idx_story_is_deleted", columnList = "is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "media_url", nullable = false, length = 500)
    String mediaUrl;

    @Column(name = "media_type", length = 20)
    @Builder.Default
    String mediaType = "IMAGE";

    @Column(name = "caption", length = 500)
    String caption;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false, length = 20)
    @Builder.Default
    PostPrivacy privacy = PostPrivacy.PUBLIC;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (expiresAt == null) {
            expiresAt = createdAt.plus(24, ChronoUnit.HOURS);
        }
    }
}
