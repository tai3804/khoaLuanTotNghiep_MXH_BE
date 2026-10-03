package iuh.fit.adminservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppealReportRequest {
    @NotBlank(message = "Appeal message is required")
    @Size(max = 1000, message = "Appeal message must not exceed 1000 characters")
    private String message;
}
