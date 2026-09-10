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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateCommentCommandHandler {

    PostRepository postRepository;
    CommentRepository commentRepository;
    MediaClient mediaClient;
    CommentFeatureMapper commentFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public CreateCommentResult handle(CreateCommentCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        Comment parent = null;
        if (command.getParentCommentId() != null) {
            parent = commentRepository.findByIdAndDeletedFalse(command.getParentCommentId())
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

        // Publish notification event to Kafka (UC-NO01)
        try {
            String snippet = command.getContent();
            if (snippet != null && snippet.length() > 60) {
                snippet = snippet.substring(0, 60) + "...";
            }
            String contentText = snippet != null ? snippet : "đã bình luận hình ảnh";

            // If replying to someone else's comment
            if (parent != null && parent.getAuthorId() != null && !parent.getAuthorId().equals(command.getAuthorId())) {
                Map<String, Object> replyEvent = new HashMap<>();
                replyEvent.put("recipientId", parent.getAuthorId().toString());
                replyEvent.put("actorId", command.getAuthorId().toString());
                replyEvent.put("type", "REPLY_COMMENT");
                replyEvent.put("title", "Phản hồi bình luận");
                replyEvent.put("content", "Một người dùng đã trả lời bình luận của bạn: \"" + contentText + "\"");
                replyEvent.put("targetId", post.getId().toString());
                replyEvent.put("targetUrl", "/posts/" + post.getId());
                replyEvent.put("avatarUrl", null);

                kafkaTemplate.send("notification.in-app.send", replyEvent);
                log.info("Published REPLY_COMMENT notification to parent author: {}", parent.getAuthorId());
            }
            // Else if commenting on someone else's post
            else if (post.getAuthorId() != null && !post.getAuthorId().equals(command.getAuthorId())) {
                Map<String, Object> notifEvent = new HashMap<>();
                notifEvent.put("recipientId", post.getAuthorId().toString());
                notifEvent.put("actorId", command.getAuthorId().toString());
                notifEvent.put("type", "COMMENT_POST");
                notifEvent.put("title", "Bình luận mới");
                notifEvent.put("content", "Một người dùng đã bình luận về bài viết của bạn: \"" + contentText + "\"");
                notifEvent.put("targetId", post.getId().toString());
                notifEvent.put("targetUrl", "/posts/" + post.getId());
                notifEvent.put("avatarUrl", null);

                kafkaTemplate.send("notification.in-app.send", notifEvent);
                log.info("Published COMMENT_POST notification to post author: {}", post.getAuthorId());
            }
        } catch (Exception e) {
            log.warn("Failed to publish comment notification event: {}", e.getMessage());
        }

        return commentFeatureMapper.toCreateResult(savedComment);
    }
}


