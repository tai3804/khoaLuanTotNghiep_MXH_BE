package iuh.fit.postservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comment_post_id", columnList = "post_id"),
        @Index(name = "idx_comment_parent_id", columnList = "parent_comment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Comment extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "author_id", nullable = false)
    UUID authorId;

    @Column(name = "parent_comment_id")
    UUID parentCommentId;

    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "media_url", length = 500)
    String mediaUrl;

    @Column(name = "media_key", length = 255)
    String mediaKey;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    long likeCount = 0;

    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    long replyCount = 0;
}
