package iuh.fit.adminservice.application.dto.request;

import iuh.fit.adminservice.domain.enums.ModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModeratePostRequest {

    @NotNull(message = "Action is required")
    ModerationAction action;

    String reason;

    @Size(max = 1000, message = "Note must not exceed 1000 characters")
    String note;
}
