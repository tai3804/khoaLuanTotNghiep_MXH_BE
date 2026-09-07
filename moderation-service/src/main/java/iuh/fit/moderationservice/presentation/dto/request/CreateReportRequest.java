package iuh.fit.moderationservice.presentation.dto.request;

import iuh.fit.moderationservice.domain.enums.ReportReason;
import iuh.fit.moderationservice.domain.enums.TargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateReportRequest {

    @NotNull(message = "{report.targetType.required}")
    TargetType targetType;

    @NotNull(message = "{report.targetId.required}")
    UUID targetId;

    @NotNull(message = "{report.reason.required}")
    ReportReason reason;

    @Size(max = 1000, message = "{report.description.size}")
    String description;
}
