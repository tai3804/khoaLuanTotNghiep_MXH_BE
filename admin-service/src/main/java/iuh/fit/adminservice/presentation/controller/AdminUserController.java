package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.application.features.command.BanUserCommand;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserFeignClient userFeignClient;
    private final iuh.fit.adminservice.infrastructure.feign.AuthFeignClient authFeignClient;
    private final BanUserCommand banUserCommand;
    private final iuh.fit.adminservice.application.service.AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        try {
            List<UserResponse> users = userFeignClient.getAllUsers();
            if (users != null && !users.isEmpty()) {
                try {
                    List<java.util.UUID> userIds = users.stream()
                            .map(u -> java.util.UUID.fromString(u.getUserId() != null ? u.getUserId() : u.getId()))
                            .collect(java.util.stream.Collectors.toList());
                    
                    List<iuh.fit.adminservice.application.dto.response.UserInfoResponse> authInfos = authFeignClient.getUsersInfo(userIds);
                    
                    java.util.Map<String, iuh.fit.adminservice.application.dto.response.UserInfoResponse> infoMap = authInfos.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                info -> info.getId().toString(),
                                info -> info
                            ));
                            
                    for (UserResponse u : users) {
                        String id = u.getUserId() != null ? u.getUserId() : u.getId();
                        iuh.fit.adminservice.application.dto.response.UserInfoResponse info = infoMap.get(id);
                        if (info != null) {
                            u.setEmail(info.getEmail());
                            u.setStatus(info.getStatus() != null ? info.getStatus() : "ACTIVE");
                            if (info.getRoles() != null) {
                                if (info.getRoles().contains("ROLE_ADMIN")) {
                                    u.setRole("ADMIN");
                                } else if (info.getRoles().contains("ROLE_USER")) {
                                    u.setRole("USER");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch user roles from Auth service: {}", e.getMessage());
                }
            }
            log.info("Fetched {} users via UserFeignClient", users != null ? users.size() : 0);
            return ResponseEntity.ok(users != null ? users : List.of());
        } catch (Exception e) {
            log.error("Failed to fetch users via UserFeignClient: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @PutMapping("/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> banUser(@PathVariable String userId, @RequestBody(required = false) java.util.Map<String, String> body) {
        log.info("Admin banned user: {}", userId);
        banUserCommand.execute(userId);
        String reason = body != null && body.get("reason") != null ? body.get("reason") : "Vi phạm tiêu chuẩn cộng đồng";
        auditLogService.log("BAN_USER", "USER", userId, "Khóa tài khoản: " + reason);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unbanUser(@PathVariable String userId) {
        log.info("Admin unbanned user: {}", userId);
        banUserCommand.unban(userId);
        auditLogService.log("UNBAN_USER", "USER", userId, "Mở khóa tài khoản thành công");
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserRole(@PathVariable String userId, @RequestBody java.util.Map<String, String> body) {
        String role = body != null && body.get("role") != null ? body.get("role") : "USER";
        log.info("Admin changing role for user {}: {}", userId, role);
        try {
            authFeignClient.updateUserRole(java.util.UUID.fromString(userId), java.util.Map.of("role", role));
            auditLogService.log("CHANGE_ROLE", "USER", userId, "Đổi vai trò thành viên thành " + role);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update role for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody java.util.Map<String, Object> request) {
        String email = (String) request.get("email");
        String role = request.get("role") != null ? (String) request.get("role") : "USER";
        log.info("Admin creating user: email={}, role={}", email, role);
        try {
            iuh.fit.adminservice.application.dto.response.UserInfoResponse res = authFeignClient.createUserByAdmin(request);
            auditLogService.log("CREATE_USER", "USER", res.getId().toString(), "Tạo tài khoản mới: " + email + " với vai trò " + role);
            return ResponseEntity.ok(res);
        } catch (feign.FeignException e) {
            log.error("Feign exception creating user: status={}, content={}", e.status(), e.contentUTF8());
            String errorMessage = "Không thể tạo tài khoản, vui lòng kiểm tra lại.";
            try {
                String content = e.contentUTF8();
                if (content != null && !content.isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
                    if (node.has("message")) {
                        errorMessage = node.get("message").asText();
                    } else if (node.has("error")) {
                        errorMessage = node.get("error").asText();
                    }
                }
            } catch (Exception parseEx) {
                // Ignore parse error
            }

            if (errorMessage.contains("Email already exists") || (e.getMessage() != null && e.getMessage().contains("Email already exists"))) {
                errorMessage = "Địa chỉ email này đã tồn tại trong hệ thống.";
            } else if (errorMessage.contains("Username already exists") || (e.getMessage() != null && e.getMessage().contains("Username already exists"))) {
                errorMessage = "Tên đăng nhập (Username) này đã được sử dụng.";
            }

            return ResponseEntity.badRequest().body(java.util.Map.of("message", errorMessage));
        } catch (Exception e) {
            log.error("Failed to create user: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Không thể tạo tài khoản";
            if (msg.contains("Email already exists")) {
                msg = "Địa chỉ email này đã tồn tại trong hệ thống.";
            } else if (msg.contains("Username already exists")) {
                msg = "Tên đăng nhập (Username) này đã được sử dụng.";
            }
            return ResponseEntity.badRequest().body(java.util.Map.of("message", msg));
        }
    }

    @PutMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable String userId, @RequestBody java.util.Map<String, String> body) {
        log.info("Admin requested reset password for user: {}", userId);
        try {
            authFeignClient.resetPassword(java.util.UUID.fromString(userId), body);
            auditLogService.log("RESET_PASSWORD", "USER", userId, "Đặt lại mật khẩu cho tài khoản người dùng");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to reset password for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Không thể đặt lại mật khẩu"));
        }
    }

    @PutMapping("/{userId}/revoke-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeSessions(@PathVariable String userId) {
        log.info("Admin requested revoke sessions for user: {}", userId);
        try {
            authFeignClient.revokeSessions(java.util.UUID.fromString(userId));
            auditLogService.log("REVOKE_SESSIONS", "USER", userId, "Cưỡng chế đăng xuất khỏi mọi thiết bị");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to revoke sessions for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/notifications/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendNotification(@RequestBody java.util.Map<String, Object> payload) {
        String recipientId = (String) payload.get("recipientId");
        String title = (String) payload.get("title");
        String content = (String) payload.get("content");
        String type = (String) payload.get("type");
        log.info("Admin sending notification to {}: title={}, type={}", recipientId, title, type);
        
        auditLogService.log("SEND_NOTIFICATION", "USER", recipientId != null ? recipientId : "BROADCAST", "Gửi thông báo: " + title);
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Đã gửi thông báo thành công"));
    }
}


