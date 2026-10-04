package iuh.fit.adminservice.application.features.user.commands.revoke_sessions;

import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.infrastructure.feign.AuthFeignClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RevokeSessionsCommandHandler {

    AuthFeignClient authFeignClient;
    AuditLogService auditLogService;

    public void handle(String userId) {
        log.info("Admin requested revoke sessions for user: {}", userId);
        authFeignClient.revokeSessions(UUID.fromString(userId));
        auditLogService.log("REVOKE_SESSIONS", "USER", userId, "Cưỡng chế đăng xuất khỏi mọi thiết bị");
    }
}
