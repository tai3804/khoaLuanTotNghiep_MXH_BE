package iuh.fit.postservice.presentation.dto.request;

import iuh.fit.postservice.domain.enums.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleReactionRequest {
    @NotNull(message = "{reaction.type.required}")
    ReactionType type;
}
