package iuh.fit.feedservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_feed_items", indexes = {
        @Index(name = "idx_user_feed_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_user_feed_post_id", columnList = "post_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedItem extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "author_id", nullable = false)
    UUID authorId;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;
}
