package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.CommentModeratedEvent;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gỡ bình luận theo quyết định kiểm duyệt, cùng cách với việc tác giả tự xóa bình luận (DeleteCommentCommandHandler):
 * bình luận gốc kéo theo các reply, bộ đếm bình luận của bài giảm tương ứng, media bị xóa và cache bài viết được làm mới.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentModeratedEventListener {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MediaClient mediaClient;
    private final PostCacheEvictor postCacheEvictor;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "comment.moderated", groupId = "post-comment-moderation-group")
    public void handleCommentModerated(CommentModeratedEvent event) {
        Optional<Comment> found = commentRepository.findById(event.getCommentId());
        if (found.isEmpty()) {
            log.warn("Comment not found for moderation event: {}", event.getCommentId());
            return;
        }
        Comment comment = found.get();
        if (comment.isDeleted()) {
            log.info("Comment {} is already deleted, skip moderation event", event.getCommentId());
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            List<Comment> toDelete = new ArrayList<>();
            toDelete.add(comment);
            if (comment.getParentCommentId() == null) {
                toDelete.addAll(commentRepository.findByParentCommentIdAndDeletedFalse(comment.getId()));
            }
            toDelete.forEach(item -> item.setDeleted(true));
            commentRepository.saveAll(toDelete);

            postRepository.findById(comment.getPostId()).ifPresent(post -> {
                post.setCommentCount(Math.max(0, post.getCommentCount() - toDelete.size()));
                postRepository.save(post);
            });
            log.info("Deleted moderated comment: {} ({} comment(s) including replies)", event.getCommentId(), toDelete.size());
        });

        if (comment.getMediaKey() != null && !comment.getMediaKey().isBlank()) {
            try {
                mediaClient.deleteFile(comment.getMediaKey());
            } catch (Exception e) {
                log.error("Failed to delete moderated comment media [{}] from S3: {}", comment.getMediaKey(), e.getMessage());
            }
        }
        // Làm mới cache sau khi giao dịch đã commit
        postCacheEvictor.evictAllWithRecheck();
    }
}
