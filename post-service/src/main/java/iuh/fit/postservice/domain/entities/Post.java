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

    @Column(name = "group_id")
    UUID groupId;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_tagged_users", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "user_id")
    @Builder.Default
    Set<UUID> taggedUserIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_hashtags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "hashtag")
    @Builder.Default
    Set<String> hashtags = new HashSet<>();

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

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    long viewCount = 0;

    @Column(name = "is_pinned", columnDefinition = "boolean default false")
    @Builder.Default
    boolean isPinned = false;

    @Column(name = "is_archived", columnDefinition = "boolean default false")
    @Builder.Default
    boolean isArchived = false;

    @Column(name = "scheduled_publish_at")
    java.time.Instant scheduledPublishAt;
}
