package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.response.UserInfoResponse;
import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.application.features.user.commands.ban_user.BanUserCommandHandler;
import iuh.fit.adminservice.application.features.user.commands.create_user.CreateUserCommandHandler;
import iuh.fit.adminservice.application.features.user.commands.reset_password.ResetPasswordCommandHandler;
import iuh.fit.adminservice.application.features.user.commands.revoke_sessions.RevokeSessionsCommandHandler;
import iuh.fit.adminservice.application.features.user.commands.send_notification.SendNotificationCommandHandler;
import iuh.fit.adminservice.application.features.user.commands.update_role.UpdateUserRoleCommandHandler;
import iuh.fit.adminservice.application.features.user.queries.get_all_users.GetAllUsersQueryHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    GetAllUsersQueryHandler getAllUsersQueryHandler;
    BanUserCommandHandler banUserCommandHandler;
    UpdateUserRoleCommandHandler updateUserRoleCommandHandler;
    CreateUserCommandHandler createUserCommandHandler;
    ResetPasswordCommandHandler resetPasswordCommandHandler;
    RevokeSessionsCommandHandler revokeSessionsCommandHandler;
    SendNotificationCommandHandler sendNotificationCommandHandler;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(getAllUsersQueryHandler.handle());
    }

    @PutMapping("/{userId}/ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> banUser(
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        banUserCommandHandler.handleBan(userId, reason);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> unbanUser(@PathVariable String userId) {
        banUserCommandHandler.handleUnban(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        String role = body != null ? body.get("role") : "USER";
        updateUserRoleCommandHandler.handle(userId, role);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {
        try {
            UserInfoResponse response = createUserCommandHandler.handle(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> resetPassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        try {
            resetPasswordCommandHandler.handle(userId, body);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Không thể đặt lại mật khẩu"));
        }
    }

    @PutMapping("/{userId}/revoke-sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> revokeSessions(@PathVariable String userId) {
        revokeSessionsCommandHandler.handle(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notifications/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> payload) {
        sendNotificationCommandHandler.handle(payload);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã gửi thông báo thành công"));
    }
}
