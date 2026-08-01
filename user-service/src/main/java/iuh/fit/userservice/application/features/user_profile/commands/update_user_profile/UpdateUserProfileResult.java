package iuh.fit.userservice.application.features.user_profile.commands.update_user_profile;

import iuh.fit.userservice.domain.enums.Gender;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateUserProfileResult {
    UUID id;
    UUID userId;
    String firstName;
    String lastName;
    String middleName;
    String avatarUrl;
    String coverUrl;
    String bio;
    LocalDate dateOfBirth;
    Gender gender;
    String location;
    String website;
    Long followerCount;
    Long followingCount;
    Long friendCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
