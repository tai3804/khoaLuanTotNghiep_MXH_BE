package iuh.fit.postservice.application.features.post.commands.apply_summary;

import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Lưu bản tóm tắt do AI tạo vào bài viết. Bản tóm tắt chỉ được áp dụng nếu nội dung bài chưa bị chỉnh sửa
 * kể từ lúc AI đọc (so sánh độ dài), tránh gắn bản tóm tắt cũ lên nội dung mới.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApplyPostSummaryCommandHandler {

    PostRepository postRepository;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public void handle(ApplyPostSummaryCommand command) {
        Optional<Post> postOpt = postRepository.findById(command.getPostId());
        if (postOpt.isEmpty()) {
            log.warn("Post {} not found. Ignoring AI summary.", command.getPostId());
            return;
        }

        Post post = postOpt.get();
        if (post.isDeleted()) {
            log.info("Post {} is deleted. Ignoring AI summary.", post.getId());
            return;
        }

        int currentLength = post.getContent() == null ? 0 : post.getContent().trim().length();
        if (currentLength != command.getContentLength()) {
            log.info("Post {} content changed since AI summarized it ({} -> {} chars). Ignoring stale summary.",
                    post.getId(), command.getContentLength(), currentLength);
            return;
        }

        post.setSummary(command.getSummary());
        postRepository.save(post);
        log.info("Saved AI summary ({} chars) for post {}", command.getSummary().length(), post.getId());
    }
}
