package iuh.fit.adminservice.application.features.user.commands.ban_user;

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
public class BanUserCommandHandler {

    AuthFeignClient authFeignClient;
    AuditLogService auditLogService;

    public void handleBan(String userId, String reason) {
        log.info("Admin banned user: {}", userId);
        authFeignClient.banUser(UUID.fromString(userId));
        String banReason = reason != null && !reason.isBlank() ? reason : "Vi phạm tiêu chuẩn cộng đồng";
        auditLogService.log("BAN_USER", "USER", userId, "Khóa tài khoản: " + banReason);
    }

    public void handleUnban(String userId) {
        log.info("Admin unbanned user: {}", userId);
        authFeignClient.unbanUser(UUID.fromString(userId));
        auditLogService.log("UNBAN_USER", "USER", userId, "Mở khóa tài khoản thành công");
    }
}
