package iuh.fit.postservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.postservice.domain.enums.ReactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "reactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_user_reaction", columnNames = {"post_id", "user_id"})
}, indexes = {
        @Index(name = "idx_reaction_post_id", columnList = "post_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Reaction extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ReactionType type;
}
