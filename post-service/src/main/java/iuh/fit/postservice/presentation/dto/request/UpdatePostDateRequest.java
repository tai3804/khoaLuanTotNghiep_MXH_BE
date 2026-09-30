package iuh.fit.postservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdatePostDateRequest {
    @NotNull(message = "Post date must not be null")
    private LocalDateTime createdAt;
}
