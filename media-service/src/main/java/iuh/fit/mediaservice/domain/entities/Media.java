package iuh.fit.mediaservice.domain.entities;

import iuh.fit.mediaservice.domain.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_items", indexes = {
        @Index(name = "idx_media_user_id", columnList = "user_id"),
        @Index(name = "idx_media_file_key", columnList = "file_key", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "file_key", nullable = false, unique = true)
    String fileKey;

    @Column(name = "file_url", nullable = false, length = 1000)
    String fileUrl;

    @Column(name = "original_filename")
    String originalFilename;

    @Column(name = "file_size", nullable = false)
    Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    MediaType mediaType;

    @Column(name = "folder")
    String folder;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
