package iuh.fit.authservice.presentation.controller.internal.v1;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.presentation.dto.response.UserInfoResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.authservice.domain.enums.UserStatus;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUserController {

    UserRepository userRepository;
    RedisCacheService redisCacheService;
    org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/info")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
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

    @PutMapping("/{userId}/ban")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
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
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unbanUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
