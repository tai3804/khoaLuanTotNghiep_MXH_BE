package iuh.fit.postservice.application.features.post.commands.share_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SharePostCommandHandler {

    PostRepository postRepository;
    PostFeatureMapper postFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public SharePostResult handle(SharePostCommand command) {
        Post originalPost = postRepository.findByIdAndDeletedFalse(command.getOriginalPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        Post sharedPost = postFeatureMapper.toSharedPostEntity(command);
        if (sharedPost.getPrivacy() == null) {
            sharedPost.setPrivacy(PostPrivacy.PUBLIC);
        }

        if (sharedPost.getPrivacy() != PostPrivacy.CUSTOM) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        } else if (sharedPost.getAllowedUserIds() == null) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        }

        Post savedPost = postRepository.save(sharedPost);

        // Increment share count on original post
        originalPost.setShareCount(originalPost.getShareCount() + 1);
        postRepository.save(originalPost);

        // Publish notification event to Kafka (UC-NO01)
        if (originalPost.getAuthorId() != null && !originalPost.getAuthorId().equals(command.getUserId())) {
            try {
                Map<String, Object> notifEvent = new HashMap<>();
                notifEvent.put("recipientId", originalPost.getAuthorId().toString());
                notifEvent.put("actorId", command.getUserId().toString());
                notifEvent.put("type", "SHARE_POST");
                notifEvent.put("title", "Chia sẻ bài viết");
                notifEvent.put("content", "Một người dùng đã chia sẻ bài viết của bạn.");
                notifEvent.put("targetId", originalPost.getId().toString());
                notifEvent.put("targetUrl", "/posts/" + originalPost.getId());
                notifEvent.put("avatarUrl", null);

                kafkaTemplate.send("notification.in-app.send", notifEvent);
                log.info("Published SHARE_POST notification event to Kafka for author: {}", originalPost.getAuthorId());
            } catch (Exception e) {
                log.warn("Failed to publish SHARE_POST notification event: {}", e.getMessage());
            }
        }

        return postFeatureMapper.toShareResult(savedPost);
    }
}

