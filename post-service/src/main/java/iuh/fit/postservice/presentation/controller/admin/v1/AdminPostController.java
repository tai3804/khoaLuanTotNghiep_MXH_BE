package iuh.fit.postservice.presentation.controller.admin.v1;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
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
@RequestMapping("/api/v1/posts/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminPostController {

    PostRepository postRepository;
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

    @GetMapping("/count")
    public ResponseEntity<Long> countPosts() {
        checkAdminPermission();
        return ResponseEntity.ok(postRepository.count());
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        checkAdminPermission();
        log.info("Admin requested delete for post: {}", postId);
        try {
            postRepository.deleteById(postId);
        } catch (Exception e) {
            log.warn("Failed to delete post {}: {}", postId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
