package iuh.fit.postservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_author", columnList = "author_id"),
        @Index(name = "idx_post_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Post extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    UUID authorId;

    @Column(columnDefinition = "TEXT")
    String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    PostPrivacy privacy = PostPrivacy.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    PostStatus status = PostStatus.PUBLISHED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_allowed_users", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "user_id")
    @Builder.Default
    Set<UUID> allowedUserIds = new HashSet<>();

    @Column(name = "original_post_id")
    UUID originalPostId;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    long likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    long commentCount = 0;

    @Column(name = "share_count", nullable = false)
    @Builder.Default
    long shareCount = 0;
}
