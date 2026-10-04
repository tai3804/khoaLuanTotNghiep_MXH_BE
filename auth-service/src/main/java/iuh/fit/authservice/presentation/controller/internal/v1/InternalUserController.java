package iuh.fit.authservice.presentation.controller.internal.v1;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.presentation.dto.request.CreateUserAdminRequest;
import iuh.fit.authservice.presentation.dto.response.UserInfoResponse;
import iuh.fit.authservice.presentation.dto.event.UserRegisteredEvent;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.authservice.domain.enums.UserStatus;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUserController {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;
    org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/info")
    public ResponseEntity<List<UserInfoResponse>> getUsersInfo(@RequestBody List<UUID> userIds) {
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
    public ResponseEntity<UserInfoResponse> createUserByAdmin(@RequestBody CreateUserAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        java.util.Set<String> roles;
        String roleStr = request.getRole() != null ? request.getRole().toUpperCase() : "USER";
        if (roleStr.contains("ADMIN")) {
            roles = java.util.Set.of("ROLE_ADMIN", "ROLE_USER");
        } else if (roleStr.contains("MODERATOR")) {
            roles = java.util.Set.of("ROLE_MODERATOR", "ROLE_USER");
        } else {
            roles = java.util.Set.of("ROLE_USER");
        }

        User user = User.builder()
                .email(request.getEmail())
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
                    .dateOfBirth(request.getDateOfBirth() != null ? request.getDateOfBirth() : java.time.LocalDate.of(2000, 1, 1))
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
    public ResponseEntity<Void> banUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.BANNED);
        
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        
        // Revoke token immediately
        redisCacheService.setTokenVersion(userId, newVersion, java.time.Duration.ofDays(7));
        
        // Push WebSocket event via Kafka to force logout
        try {
            kafkaTemplate.send("notification.in-app.send", java.util.Map.of(
                "recipientId", userId.toString(),
                "type", "SYSTEM",
                "title", "ACCOUNT_BANNED",
                "content", "Tài khoản của bạn đã bị khóa."
            ));
        } catch (Exception e) {
            // Ignore error if Kafka is down
        }
        
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateUserRole(@PathVariable UUID userId, @RequestBody java.util.Map<String, String> body) {
        String newRole = body != null ? body.get("role") : null;
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if ("ADMIN".equalsIgnoreCase(newRole) || "ROLE_ADMIN".equalsIgnoreCase(newRole)) {
            user.setRoles(java.util.Set.of("ROLE_ADMIN", "ROLE_USER"));
        } else if ("MODERATOR".equalsIgnoreCase(newRole) || "ROLE_MODERATOR".equalsIgnoreCase(newRole)) {
            user.setRoles(java.util.Set.of("ROLE_MODERATOR", "ROLE_USER"));
        } else {
            user.setRoles(java.util.Set.of("ROLE_USER"));
        }

        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        // Revoke token so user logs in or refreshes with new role
        redisCacheService.setTokenVersion(userId, newVersion, java.time.Duration.ofDays(7));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId, @RequestBody java.util.Map<String, String> body) {
        String newPassword = body != null ? body.get("newPassword") : null;
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        redisCacheService.setTokenVersion(userId, newVersion, java.time.Duration.ofDays(7));
        log.info("Admin reset password and invalidated tokens for userId: {}", userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(@PathVariable UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        redisCacheService.setTokenVersion(userId, newVersion, java.time.Duration.ofDays(7));
        log.info("Admin revoked all active sessions for userId: {}", userId);
        return ResponseEntity.ok().build();
    }
}

