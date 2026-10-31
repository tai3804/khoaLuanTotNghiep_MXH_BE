package iuh.fit.authservice.presentation.controller.internal.v1;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.presentation.dto.request.CreateUserAdminRequest;
import iuh.fit.authservice.presentation.dto.response.UserInfoResponse;
import iuh.fit.authservice.presentation.dto.event.UserRegisteredEvent;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUserController {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/info")
    public ResponseEntity<List<UserInfoResponse>> getUsersInfo(@RequestBody List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = userRepository.findAllById(userIds);

        List<UserInfoResponse> response = users.stream().map(user ->
                UserInfoResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .roles(user.getRoles())
                        .status(user.getStatus() != null ? user.getStatus().name() : "ACTIVE")
                        .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<UserInfoResponse> createUserByAdmin(@RequestBody CreateUserAdminRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Set<String> roles;
        String roleStr = request.getRole() != null ? request.getRole().toUpperCase() : "USER";
        if (roleStr.contains("ADMIN")) {
            roles = Set.of("ROLE_ADMIN", "ROLE_USER");
        } else if (roleStr.contains("MODERATOR")) {
            roles = Set.of("ROLE_MODERATOR", "ROLE_USER");
        } else {
            roles = Set.of("ROLE_USER");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .tokenVersion(1)
                .build();

        User savedUser = userRepository.save(user);

        // Broadcast UserRegisteredEvent for UserProfile creation in user-service
        try {
            String firstName = request.getFirstName() != null && !request.getFirstName().isBlank()
                    ? request.getFirstName()
                    : "Người dùng";
            String lastName = request.getLastName() != null && !request.getLastName().isBlank()
                    ? request.getLastName()
                    : "";

            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .userId(savedUser.getId())
                    .email(savedUser.getEmail())
                    .firstName(firstName)
                    .lastName(lastName)
                    .dateOfBirth(request.getDateOfBirth() != null ? request.getDateOfBirth() : LocalDate.of(2000, 1, 1))
                    .gender(request.getGender() != null ? request.getGender() : "OTHER")
                    .build();

            kafkaTemplate.send("user.registered", event);
            log.info("Sent user.registered event for Admin-created userId: {}", savedUser.getId());
        } catch (Exception e) {
            log.warn("Could not publish user.registered event for created user: {}", e.getMessage());
        }

        return ResponseEntity.ok(UserInfoResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .roles(savedUser.getRoles())
                .status(savedUser.getStatus().name())
                .build());
    }

    @PutMapping("/{userId}/ban")
    @Transactional
    public ResponseEntity<Void> banUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.BANNED);

        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        deviceRepository.deleteAllByUserId(userId);

        // Revoke token immediately
        redisCacheService.setTokenVersion(userId, newVersion, Duration.ofDays(7));

        // Push WebSocket event via Kafka to force logout
        try {
            kafkaTemplate.send("notification.in-app.send", Map.of(
                    "recipientId", userId.toString(),
                    "type", "SYSTEM",
                    "title", "ACCOUNT_BANNED",
                    "content", "Tài khoản của bạn đã bị khóa."
            ));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    @Transactional
    public ResponseEntity<Void> unbanUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/role")
    @Transactional
    public ResponseEntity<Void> updateUserRole(@PathVariable UUID userId, @RequestBody Map<String, String> body) {
        String newRole = body != null ? body.get("role") : null;
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        if ("ADMIN".equalsIgnoreCase(newRole) || "ROLE_ADMIN".equalsIgnoreCase(newRole)) {
            user.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
        } else if ("MODERATOR".equalsIgnoreCase(newRole) || "ROLE_MODERATOR".equalsIgnoreCase(newRole)) {
            user.setRoles(Set.of("ROLE_MODERATOR", "ROLE_USER"));
        } else {
            user.setRoles(Set.of("ROLE_USER"));
        }

        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        redisCacheService.setTokenVersion(userId, newVersion, Duration.ofDays(7));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/reset-password")
    @Transactional
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId, @RequestBody Map<String, String> body) {
        String newPassword = body != null ? body.get("newPassword") : null;
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        user.setPassword(passwordEncoder.encode(newPassword));
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        deviceRepository.deleteAllByUserId(userId);
        redisCacheService.setTokenVersion(userId, newVersion, Duration.ofDays(7));
        log.info("Admin reset password and invalidated tokens for userId: {}", userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/revoke-sessions")
    @Transactional
    public ResponseEntity<Void> revokeSessions(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        deviceRepository.deleteAllByUserId(userId);
        redisCacheService.setTokenVersion(userId, newVersion, Duration.ofDays(7));
        log.info("Admin revoked all active sessions for userId: {}", userId);
        return ResponseEntity.ok().build();
    }
}
