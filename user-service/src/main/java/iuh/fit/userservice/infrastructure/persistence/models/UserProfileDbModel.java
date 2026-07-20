package iuh.fit.userservice.infrastructure.persistence.models;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.userservice.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileDbModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "avatar_url")
    String avatarUrl;

    @Column(name = "cover_url")
    String coverUrl;

    @Column(columnDefinition = "TEXT")
    String bio;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    Gender gender;

    String location;

    String website;

    @Column(name = "follower_count", nullable = false)
    @Builder.Default
    Long followerCount = 0L;

    @Column(name = "following_count", nullable = false)
    @Builder.Default
    Long followingCount = 0L;

    @Column(name = "friend_count", nullable = false)
    @Builder.Default
    Long friendCount = 0L;
}
