package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Object id;
    private String adminId;
    private String adminUsername;
    private String action;
    private String targetType;
    private String targetId;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}
