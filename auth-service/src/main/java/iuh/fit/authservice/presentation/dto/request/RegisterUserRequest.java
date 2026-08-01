package iuh.fit.authservice.presentation.dto.request;

import iuh.fit.authservice.presentation.constants.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserRequest {
    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    String email;

    @NotBlank(message = "{user.password.required}")
    @Size(min = 6, message = "{user.password.size}")
    String password;

    @NotBlank(message = "{user.firstName.required}")
    @Size(max = 50, message = "{user.firstName.size}")
    String firstName;

    @NotBlank(message = "{user.lastName.required}")
    @Size(max = 50, message = "{user.lastName.size}")
    String lastName;

    @Size(max = 50, message = "{user.middleName.size}")
    String middleName;

    LocalDate dateOfBirth;

    @Pattern(regexp = ValidationConstants.GENDER_REGEX, message = "{user.gender.invalid}")
    String gender;
}
