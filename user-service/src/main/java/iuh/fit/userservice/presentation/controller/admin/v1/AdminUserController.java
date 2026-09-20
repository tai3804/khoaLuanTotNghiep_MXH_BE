package iuh.fit.userservice.presentation.controller.admin.v1;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/users/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    UserProfileRepository userProfileRepository;
    JwtUtil jwtUtil;

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<UserProfile> profiles = userProfileRepository.findAll();
        List<Map<String, Object>> response = profiles.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            String uid = p.getUserId() != null ? p.getUserId().toString() : (p.getId() != null ? p.getId().toString() : "");
            String firstName = p.getFirstName() != null ? p.getFirstName() : "";
            String lastName = p.getLastName() != null ? p.getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (fullName.isEmpty()) {
                fullName = "Người dùng";
            }

            map.put("id", uid);
            map.put("userId", uid);
            map.put("firstName", firstName);
            map.put("lastName", lastName);
            map.put("fullName", fullName);
            map.put("avatarUrl", p.getAvatarUrl());
            map.put("coverUrl", p.getCoverUrl());
            map.put("bio", p.getBio());
            map.put("gender", p.getGender() != null ? p.getGender().name() : "OTHER");
            map.put("status", "ACTIVE");
            map.put("role", "USER");
            map.put("followerCount", p.getFollowerCount() != null ? p.getFollowerCount() : 0);
            map.put("followingCount", p.getFollowingCount() != null ? p.getFollowingCount() : 0);
            map.put("friendCount", p.getFriendCount() != null ? p.getFriendCount() : 0);
            map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : new Date().toInstant().toString());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> countUsers() {
        return ResponseEntity.ok(userProfileRepository.count());
    }

    @PutMapping("/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> banUser(@PathVariable String userId) {
        log.info("Admin requested ban for user: {}", userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unbanUser(@PathVariable String userId) {
        log.info("Admin requested unban for user: {}", userId);
        return ResponseEntity.ok().build();
    }
}
