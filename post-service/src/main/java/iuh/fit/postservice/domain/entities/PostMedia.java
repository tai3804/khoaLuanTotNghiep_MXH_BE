package iuh.fit.postservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.postservice.domain.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "post_media", indexes = {
        @Index(name = "idx_media_post_id", columnList = "post_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostMedia extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "file_url", nullable = false, length = 500)
    String fileUrl;

    @Column(name = "file_key", nullable = false, length = 255)
    String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    MediaType mediaType;

    @Column(name = "file_size", nullable = false)
    long fileSize;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    int sortOrder = 0;
}
