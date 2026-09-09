package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleReactionRequest {

    @NotBlank(message = "Emoji reaction is required")
    String emoji;
}
