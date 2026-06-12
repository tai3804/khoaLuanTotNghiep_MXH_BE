package iuh.fit.graduationthesis.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {

    @NotBlank(message = "{validation.username.not_blank}")
    @Size(min = 3, message = "{validation.username.size}")
    String userName;

    @NotBlank(message = "{validation.password.not_blank}")
    @Size(min = 8, message = "{validation.password.size}")
    String password;
}
