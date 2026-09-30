package iuh.fit.postservice.application.features.comment.commands.update_comment;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.application.mapper.CommentFeatureMapper;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateCommentCommandHandler {

    private final CommentRepository commentRepository;
    private final CommentFeatureMapper commentFeatureMapper;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public CreateCommentResult handle(UpdateCommentCommand command) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(command.getCommentId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getPostId().equals(command.getPostId()) || !comment.getAuthorId().equals(command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        comment.setContent(command.getContent().trim());
        return commentFeatureMapper.toCreateResult(commentRepository.save(comment));
    }
}
