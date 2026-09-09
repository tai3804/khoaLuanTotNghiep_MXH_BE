package iuh.fit.postservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_viewers", indexes = {
        @Index(name = "idx_sv_story_viewer", columnList = "story_id, viewer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryViewer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "story_id", nullable = false)
    UUID storyId;

    @Column(name = "viewer_id", nullable = false)
    UUID viewerId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    Instant viewedAt;

    @PrePersist
    protected void onCreate() {
        viewedAt = Instant.now();
    }
}
