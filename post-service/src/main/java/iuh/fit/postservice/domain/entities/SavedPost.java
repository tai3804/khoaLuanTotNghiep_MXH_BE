package iuh.fit.postservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_posts", indexes = {
        @Index(name = "idx_sp_user_post", columnList = "user_id, post_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "collection_name", length = 100)
    @Builder.Default
    String collectionName = "DEFAULT";

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
