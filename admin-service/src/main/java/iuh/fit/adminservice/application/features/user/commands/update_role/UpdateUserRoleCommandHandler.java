package iuh.fit.adminservice.application.features.user.commands.update_role;

import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.infrastructure.feign.AuthFeignClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdateUserRoleCommandHandler {

    AuthFeignClient authFeignClient;
    AuditLogService auditLogService;

    public void handle(String userId, String role) {
        String targetRole = role != null && !role.isBlank() ? role : "USER";
        log.info("Admin changing role for user {}: {}", userId, targetRole);
        authFeignClient.updateUserRole(UUID.fromString(userId), Map.of("role", targetRole));
        auditLogService.log("CHANGE_ROLE", "USER", userId, "Đổi vai trò thành viên thành " + targetRole);
    }
}
