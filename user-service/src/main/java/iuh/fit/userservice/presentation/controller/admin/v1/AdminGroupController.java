package iuh.fit.userservice.presentation.controller.admin.v1;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.repository.GroupRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminGroupController {

    GroupRepository groupRepository;
    JwtUtil jwtUtil;

    private void checkAdminPermission() {
        List<String> roles = jwtUtil.getCurrentUserRoles();
        if (roles != null && !roles.isEmpty()) {
            boolean isAdmin = roles.contains("ROLE_ADMIN") || roles.contains("ADMIN");
            if (!isAdmin) {
                log.warn("Access denied for non-admin user: {}", jwtUtil.getCurrentUserId());
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<Group>> getAllGroups() {
        checkAdminPermission();
        return ResponseEntity.ok(groupRepository.findAll());
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
        checkAdminPermission();
        log.info("Admin requested delete for group: {}", groupId);
        try {
            groupRepository.deleteById(groupId);
        } catch (Exception e) {
            log.warn("Failed to delete group {}: {}", groupId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
