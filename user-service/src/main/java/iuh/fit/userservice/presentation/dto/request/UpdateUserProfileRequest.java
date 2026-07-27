package iuh.fit.userservice.presentation.dto.request;

import iuh.fit.userservice.domain.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateUserProfileRequest {

    @NotBlank(message = "{userProfile.firstName.required}")
    @Size(max = 50, message = "{userProfile.firstName.size}")
    String firstName;

    @NotBlank(message = "{userProfile.lastName.required}")
    @Size(max = 50, message = "{userProfile.lastName.size}")
    String lastName;

    @Size(max = 50, message = "{userProfile.middleName.size}")
    String middleName;

    @Size(max = 500, message = "{userProfile.avatarUrl.size}")
    String avatarUrl;

    @Size(max = 500, message = "{userProfile.coverUrl.size}")
    String coverUrl;

    @Size(max = 1000, message = "{userProfile.bio.size}")
    String bio;

    @Past(message = "{userProfile.dateOfBirth.past}")
    LocalDate dateOfBirth;

    Gender gender;

    @Size(max = 100, message = "{userProfile.location.size}")
    String location;

    @Size(max = 200, message = "{userProfile.website.size}")
    String website;
}
