package iuh.fit.postservice.application.features.comment.commands.create_comment;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.CommentFeatureMapper;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import iuh.fit.postservice.infrastructure.client.media.dto.MediaClientResponse;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
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
public class CreateCommentCommandHandler {

    PostRepository postRepository;
    CommentRepository commentRepository;
    MediaClient mediaClient;
    CommentFeatureMapper commentFeatureMapper;

    @Transactional
    public CreateCommentResult handle(CreateCommentCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (command.getParentCommentId() != null) {
            Comment parent = commentRepository.findByIdAndDeletedFalse(command.getParentCommentId())
                    .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));
            parent.setReplyCount(parent.getReplyCount() + 1);
            commentRepository.save(parent);
        }

        String mediaUrl = null;
        String mediaKey = null;

        if (command.getFile() != null && !command.getFile().isEmpty()) {
            try {
                ApiResponse<MediaClientResponse> mediaResponse = mediaClient.uploadFile(command.getFile(), "comments");
                if (mediaResponse != null && mediaResponse.getData() != null) {
                    mediaUrl = mediaResponse.getData().getFileUrl();
                    mediaKey = mediaResponse.getData().getFileKey();
                }
            } catch (Exception e) {
                log.error("Failed to upload comment media to media-service: {}", e.getMessage(), e);
                throw new BusinessException(PostServiceErrorCode.MEDIA_UPLOAD_FAILED);
            }
        }

        Comment comment = commentFeatureMapper.toEntity(command, mediaUrl, mediaKey);
        Comment savedComment = commentRepository.save(comment);

        // Increment comment count on post
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        return commentFeatureMapper.toCreateResult(savedComment);
    }
}
