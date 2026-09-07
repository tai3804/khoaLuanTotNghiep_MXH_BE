package iuh.fit.moderationservice.presentation.dto.request;

import iuh.fit.moderationservice.domain.enums.ModerationAction;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessReportRequest {

    @NotNull(message = "{moderationLog.action.required}")
    ModerationAction action;

    String note;
}
