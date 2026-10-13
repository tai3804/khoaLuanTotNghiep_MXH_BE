package iuh.fit.postservice.presentation.controller.admin.v1;

import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * /api/v1/posts/** là đường dẫn công khai ở tầng filter nên quyền phải được kiểm tra bằng @PreAuthorize;
 * kiểm tra thủ công trước đây bỏ qua khi danh sách role rỗng (request chưa đăng nhập) nên ai cũng xoá được bài.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/posts/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class AdminPostController {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    MediaClient mediaClient;
    PostCacheEvictor postCacheEvictor;

    @GetMapping("/count")
    public ResponseEntity<Long> countPosts() {
        return ResponseEntity.ok(postRepository.count());
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        log.info("Admin requested delete for post: {}", postId);
        try {
            List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(postId);
            for (PostMedia media : mediaList) {
                try {
                    String targetKey = (media.getFileUrl() != null && !media.getFileUrl().isBlank())
                            ? media.getFileUrl()
                            : media.getFileKey();
                    if (targetKey != null && !targetKey.isBlank()) {
                        mediaClient.deleteFile(targetKey);
                    }
                } catch (Exception e) {
                    log.warn("Admin delete: Failed to delete media [{}] from S3: {}", media.getFileKey(), e.getMessage());
                }
            }
            postMediaRepository.deleteByPostId(postId);
            postRepository.deleteById(postId);
            postCacheEvictor.evictAllWithRecheck();
        } catch (Exception e) {
            log.warn("Failed to delete post {}: {}", postId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
