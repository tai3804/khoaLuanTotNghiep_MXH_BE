package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostPublicationHelper {

    KafkaTemplate<String, Object> kafkaTemplate;
    GroupNotificationPublisher groupNotificationPublisher;
    LiveNotificationPublisher liveNotificationPublisher;
    PostRepository postRepository;
    PostCacheEvictor postCacheEvictor;
    TransactionTemplate transactionTemplate;

    /**
     * Gửi các thông báo sau khi bài viết đã chính thức PUBLISHED (TAG_POST, thông báo nhóm, phát trực tiếp).
     */
    public void notifyPostPublished(Post post) {
        if (post == null || post.getStatus() != PostStatus.PUBLISHED || post.isDeleted() || post.isArchived()) {
            return;
        }

        Set<UUID> taggedUsers = post.getTaggedUserIds();
        if (taggedUsers != null && !taggedUsers.isEmpty()) {
            for (UUID taggedUserId : taggedUsers) {
                if (taggedUserId != null && !taggedUserId.equals(post.getAuthorId())) {
                    try {
                        Map<String, Object> tagEvent = new HashMap<>();
                        tagEvent.put("recipientId", taggedUserId.toString());
                        tagEvent.put("actorId", post.getAuthorId().toString());
                        tagEvent.put("type", "TAG_POST");
                        tagEvent.put("title", "Gắn thẻ trong bài viết");
                        tagEvent.put("content", "Một người dùng đã gắn thẻ bạn trong một bài viết.");
                        tagEvent.put("targetId", post.getId().toString());
                        tagEvent.put("targetUrl", "/posts/" + post.getId());
                        tagEvent.put("avatarUrl", null);

                        kafkaTemplate.send("notification.in-app.send", tagEvent);
                        log.info("Published TAG_POST notification to tagged user: {}", taggedUserId);
                    } catch (Exception e) {
                        log.warn("Failed to publish TAG_POST notification for user {}: {}", taggedUserId, e.getMessage());
                    }
                }
            }
        }

        if (post.getGroupId() != null) {
            groupNotificationPublisher.postPublished(post);
        }

        liveNotificationPublisher.liveStarted(post);
    }

    /**
     * Dùng khi bài viết chuyển từ SCHEDULED hoặc PENDING_APPROVAL sang PUBLISHED:
     * phát PostCreatedEvent (không kèm file) để feed-service fan-out và ai-service kiểm duyệt/tóm tắt,
     * đồng thời gửi thông báo gắn thẻ / nhóm / livestream.
     */
    public void publishAndNotifyDeferredPost(Post post) {
        if (post == null || post.getStatus() != PostStatus.PUBLISHED || post.isDeleted() || post.isArchived()) {
            return;
        }

        try {
            PostCreatedEvent event = PostCreatedEvent.builder()
                    .postId(post.getId())
                    .authorId(post.getAuthorId())
                    .content(post.getContent())
                    .files(List.of())
                    .build();
            kafkaTemplate.send("post.created", event);
            log.info("Published deferred PostCreatedEvent to 'post.created' for postId: {}", post.getId());
        } catch (Exception e) {
            log.warn("Failed to publish deferred PostCreatedEvent for postId {}: {}", post.getId(), e.getMessage());
        }

        notifyPostPublished(post);
    }

    /**
     * Khi việc gửi gói media lên Kafka thất bại (ví dụ RecordTooLargeException),
     * chuyển trạng thái bài viết sang FAILED để không bị kẹt ở PROCESSING vĩnh viễn.
     */
    public void markMediaUploadFailed(UUID postId, Throwable cause) {
        log.error("Kafka send failed for post.created media upload on post {}: {}", postId, cause != null ? cause.getMessage() : "unknown");
        try {
            transactionTemplate.executeWithoutResult(status ->
                    postRepository.findByIdAndDeletedFalse(postId).ifPresent(post -> {
                        if (post.getStatus() == PostStatus.PROCESSING) {
                            post.setStatus(PostStatus.FAILED);
                            postRepository.save(post);
                        }
                    })
            );
            postCacheEvictor.evictAllWithRecheck();
        } catch (Exception e) {
            log.error("Failed to mark post {} as FAILED after Kafka send error: {}", postId, e.getMessage());
        }
    }
}

