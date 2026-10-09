package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_view_histories", indexes = {
        @Index(name = "idx_pvh_target_user", columnList = "target_user_id"),
        @Index(name = "idx_pvh_viewer", columnList = "viewer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileViewHistory extends BaseEntity {

    @Column(name = "target_user_id", nullable = false)
    UUID targetUserId;

    @Column(name = "viewer_id", nullable = false)
    UUID viewerId;

    @Column(name = "last_viewed_at", nullable = false)
    @Builder.Default
    LocalDateTime lastViewedAt = LocalDateTime.now();
}
