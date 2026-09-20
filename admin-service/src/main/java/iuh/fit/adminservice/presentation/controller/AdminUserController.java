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
    public ResponseEntity<Void> banUser(@PathVariable String userId) {
        log.info("Admin banned user: {}", userId);
        banUserCommand.execute(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unbanUser(@PathVariable String userId) {
        log.info("Admin unbanned user: {}", userId);
        banUserCommand.unban(userId);
        return ResponseEntity.ok().build();
    }
}


