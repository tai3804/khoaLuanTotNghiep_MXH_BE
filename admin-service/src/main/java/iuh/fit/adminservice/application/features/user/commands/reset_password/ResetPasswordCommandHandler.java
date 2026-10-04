package iuh.fit.adminservice.application.features.user.commands.reset_password;

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
public class ResetPasswordCommandHandler {

    AuthFeignClient authFeignClient;
    AuditLogService auditLogService;

    public void handle(String userId, Map<String, String> body) {
        log.info("Admin requested reset password for user: {}", userId);
        authFeignClient.resetPassword(UUID.fromString(userId), body);
        auditLogService.log("RESET_PASSWORD", "USER", userId, "Đặt lại mật khẩu cho tài khoản người dùng");
    }
}
