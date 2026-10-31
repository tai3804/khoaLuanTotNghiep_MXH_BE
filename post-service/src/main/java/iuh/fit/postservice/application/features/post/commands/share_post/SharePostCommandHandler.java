package iuh.fit.postservice.application.features.post.commands.share_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SharePostCommandHandler {

    PostRepository postRepository;
    PostFeatureMapper postFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;
    ReputationGuard reputationGuard;
    PostVisibilityService postVisibilityService;
    UserConnectionClient userConnectionClient;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public SharePostResult handle(SharePostCommand command) {
        reputationGuard.checkCanPost(command.getUserId(), false);
        Post originalPost = postRepository.findByIdAndDeletedFalse(command.getOriginalPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (originalPost.getStatus() != PostStatus.PUBLISHED
                || originalPost.isArchived()
                || !postVisibilityService.canView(originalPost, command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        // Prevent leaking private group posts onto external profiles/feeds via share
        if (originalPost.getGroupId() != null) {
            try {
                var groupRes = userConnectionClient.getGroupFeedVisibility(List.of(originalPost.getGroupId()));
                var groupVis = (groupRes != null && groupRes.getData() != null)
                        ? groupRes.getData().stream()
                                .filter(g -> originalPost.getGroupId().equals(g.groupId()))
                                .findFirst()
                                .orElse(null)
                        : null;
                if (groupVis == null || !groupVis.publicGroup()) {
                    throw new BusinessException(PostServiceErrorCode.GROUP_PRIVATE);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(PostServiceErrorCode.GROUP_SERVICE_UNAVAILABLE);
            }
        }

        Post sharedPost = postFeatureMapper.toSharedPostEntity(command);
        sharedPost.setOriginalPostId(originalPost.getId());
        if (sharedPost.getPrivacy() == null) {
            sharedPost.setPrivacy(PostPrivacy.PUBLIC);
        }

        if (sharedPost.getPrivacy() != PostPrivacy.CUSTOM) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        } else if (sharedPost.getAllowedUserIds() == null) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        }

        sharedPost.setLastActivityAt(java.time.LocalDateTime.now());
        Post savedPost = postRepository.save(sharedPost);

        originalPost.setShareCount(originalPost.getShareCount() + 1);
        postRepository.save(originalPost);

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
