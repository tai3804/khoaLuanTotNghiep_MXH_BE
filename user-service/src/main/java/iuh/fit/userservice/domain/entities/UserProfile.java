package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.userservice.domain.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
public class UserProfile extends BaseEntity {

    @NotNull(message = "{userProfile.userId.required}")
    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @NotBlank(message = "{userProfile.firstName.required}")
    @Size(max = 50, message = "{userProfile.firstName.size}")
    @Column(name = "first_name", nullable = false, length = 50)
    String firstName;

    @NotBlank(message = "{userProfile.lastName.required}")
    @Size(max = 50, message = "{userProfile.lastName.size}")
    @Column(name = "last_name", nullable = false, length = 50)
    String lastName;

    @Size(max = 50, message = "{userProfile.middleName.size}")
    @Column(name = "middle_name", length = 50)
    String middleName;

    @Size(max = 500, message = "{userProfile.avatarUrl.size}")
    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Size(max = 500, message = "{userProfile.coverUrl.size}")
    @Column(name = "cover_url", length = 500)
    String coverUrl;

    @Size(max = 1000, message = "{userProfile.bio.size}")
    @Column(columnDefinition = "TEXT")
    String bio;

    @Past(message = "{userProfile.dateOfBirth.past}")
    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    Gender gender;

    @Size(max = 100, message = "{userProfile.location.size}")
    @Column(length = 100)
    String location;

    @Size(max = 200, message = "{userProfile.website.size}")
    @Column(length = 200)
    String website;

    @NotNull(message = "{userProfile.followerCount.required}")
    @Min(value = 0, message = "{userProfile.followerCount.min}")
    @Column(name = "follower_count", nullable = false)
    @Builder.Default
    Long followerCount = 0L;

    @NotNull(message = "{userProfile.followingCount.required}")
    @Min(value = 0, message = "{userProfile.followingCount.min}")
    @Column(name = "following_count", nullable = false)
    @Builder.Default
    Long followingCount = 0L;

    @NotNull(message = "{userProfile.friendCount.required}")
    @Min(value = 0, message = "{userProfile.friendCount.min}")
    @Column(name = "friend_count", nullable = false)
    @Builder.Default
    Long friendCount = 0L;
}
