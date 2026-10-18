package iuh.fit.postservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một người dùng đã xem một bài viết trên bảng tin (đủ lâu và đủ lớn trên màn hình).
 * Dùng để không đưa lại những bài đã xem vào bảng tin ở lần tải sau.
 */
@Entity
@Table(name = "post_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_view_user_post", columnNames = {"user_id", "post_id"}),
        indexes = @Index(name = "idx_post_view_user_seen", columnList = "user_id, seen_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "seen_at", nullable = false)
    LocalDateTime seenAt;
}
