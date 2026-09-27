package iuh.fit.adminservice.application.service;

import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SettingsRepository settingsRepository;
    private final JwtUtil jwtUtil;

    public void log(String action, String targetType, String targetId, String details) {
        try {
            String adminId = jwtUtil.getCurrentUserId();
            if (adminId == null || adminId.isBlank()) {
                adminId = "SYSTEM/ADMIN";
            }
            String adminEmail = jwtUtil.getClaim("email");
            String adminUsername = adminEmail != null ? adminEmail.split("@")[0] : "admin";

            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .adminUsername(adminUsername)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .details(details)
                    .ipAddress("127.0.0.1")
                    .createdAt(LocalDateTime.now())
                    .build();

            settingsRepository.saveAuditLog(auditLog);
            log.info("Recorded audit log: action={}, targetType={}, targetId={}", action, targetType, targetId);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}
