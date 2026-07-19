package iuh.fit.notificationservice.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendEmailRequest {
    
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    String toEmail;
    
    @NotBlank(message = "Reset token is required")
    String resetToken;
}
