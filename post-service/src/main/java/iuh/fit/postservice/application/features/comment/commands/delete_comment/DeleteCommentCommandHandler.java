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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteCommentCommandHandler {

    CommentRepository commentRepository;
    PostRepository postRepository;
    MediaClient mediaClient;

    @Transactional
    public void handle(DeleteCommentCommand command) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(command.getCommentId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getAuthorId().equals(command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        comment.setDeleted(true);
        commentRepository.save(comment);

        if (comment.getMediaKey() != null && !comment.getMediaKey().isBlank()) {
            try {
                mediaClient.deleteFile(comment.getMediaKey());
            } catch (Exception e) {
                log.error("Failed to delete comment media [{}] from S3: {}", comment.getMediaKey(), e.getMessage());
            }
        }

        // Decrement comment count on post
        postRepository.findByIdAndDeletedFalse(comment.getPostId()).ifPresent(post -> {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            postRepository.save(post);
        });
    }
}
