package iuh.fit.postservice.application.features.comment.commands.delete_comment;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
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
    JwtUtil jwtUtil;

    @Transactional
    @CacheEvict(cacheNames = { "post-feed-v2", "post-user-feed-v2", "post-detail-v2" }, allEntries = true)
    public void handle(DeleteCommentCommand command) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(command.getCommentId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (command.getPostId() != null && !comment.getPostId().equals(command.getPostId())) {
            throw new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND);
        }

        Post post = postRepository.findByIdAndDeletedFalse(comment.getPostId()).orElse(null);
        boolean isCommentAuthor = comment.getAuthorId().equals(command.getUserId());
        boolean isPostAuthor = post != null && post.getAuthorId().equals(command.getUserId());
        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean isStaff = roles != null && roles.stream().anyMatch(r ->
                "ROLE_ADMIN".equalsIgnoreCase(r) ||
                "ADMIN".equalsIgnoreCase(r) ||
                "ROLE_MODERATOR".equalsIgnoreCase(r) ||
                "MODERATOR".equalsIgnoreCase(r)
        );

        if (!isCommentAuthor && !isPostAuthor && !isStaff) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        List<Comment> commentsToDelete = new ArrayList<>();
        commentsToDelete.add(comment);
        if (comment.getParentCommentId() == null) {
            commentsToDelete.addAll(commentRepository.findByParentCommentIdAndDeletedFalse(comment.getId()));
        }
        commentsToDelete.forEach(item -> item.setDeleted(true));
        commentRepository.saveAll(commentsToDelete);

        if (comment.getParentCommentId() != null) {
            commentRepository.findByIdAndDeletedFalse(comment.getParentCommentId()).ifPresent(parent -> {
                parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
                commentRepository.save(parent);
            });
        }

        for (Comment deletedComment : commentsToDelete) {
            if (deletedComment.getMediaKey() != null && !deletedComment.getMediaKey().isBlank()) {
                try {
                    mediaClient.deleteFile(deletedComment.getMediaKey());
                } catch (Exception e) {
                    log.error("Failed to delete comment media [{}] from S3: {}", deletedComment.getMediaKey(), e.getMessage());
                }
            }
        }

        if (post != null) {
            long realCommentCount = commentRepository.countByPostIdAndDeletedFalse(post.getId());
            post.setCommentCount(realCommentCount);
            postRepository.save(post);
        }
    }
}
