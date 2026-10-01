package iuh.fit.adminservice.application.dto.request;

import iuh.fit.adminservice.domain.enums.ReportReason;
import iuh.fit.adminservice.domain.enums.TargetType;
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

    @NotNull(message = "Target ID is required")
    UUID targetId;

    @NotNull(message = "Target type is required")
    TargetType targetType;

    @NotNull(message = "Report reason is required")
    ReportReason reason;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description;
}
