package iuh.fit.postservice.application.features.comment.commands.delete_comment;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteCommentCommandHandler {

    CommentRepository commentRepository;
    PostRepository postRepository;
    MediaClient mediaClient;

    @Transactional
    @CacheEvict(cacheNames = { "post-feed-v2", "post-user-feed-v2", "post-detail-v2" }, allEntries = true)
    public void handle(DeleteCommentCommand command) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(command.getCommentId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        Post post = postRepository.findByIdAndDeletedFalse(comment.getPostId()).orElse(null);
        boolean isCommentAuthor = comment.getAuthorId().equals(command.getUserId());
        boolean isPostAuthor = post != null && post.getAuthorId().equals(command.getUserId());

        if (!isCommentAuthor && !isPostAuthor) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        // A root comment owns its replies in the UI. Soft-delete them as well so
        // they cannot remain visible through a direct replies request.
        List<Comment> commentsToDelete = new ArrayList<>();
        commentsToDelete.add(comment);
        if (comment.getParentCommentId() == null) {
            commentsToDelete.addAll(commentRepository.findByParentCommentIdAndDeletedFalse(comment.getId()));
        }
        commentsToDelete.forEach(item -> item.setDeleted(true));
        commentRepository.saveAll(commentsToDelete);

        if (comment.getMediaKey() != null && !comment.getMediaKey().isBlank()) {
            try {
                mediaClient.deleteFile(comment.getMediaKey());
            } catch (Exception e) {
                log.error("Failed to delete comment media [{}] from S3: {}", comment.getMediaKey(), e.getMessage());
            }
        }

        // Decrement comment count on post
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - commentsToDelete.size()));
            postRepository.save(post);
        }
    }
}
