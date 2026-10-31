package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostMediaUploadedEvent;
import iuh.fit.postservice.application.service.PostPublicationHelper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostMediaEventListener {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostCacheEvictor postCacheEvictor;
    PostPublicationHelper postPublicationHelper;

    @Transactional
    @KafkaListener(topics = "post.media.uploaded", groupId = "post-group")
    public void handlePostMediaUploaded(PostMediaUploadedEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }
        log.info("Received PostMediaUploadedEvent for postId: {}, success: {}", event.getPostId(), event.isSuccess());

        Optional<Post> postOptional = postRepository.findByIdAndDeletedFalse(event.getPostId());
        if (postOptional.isEmpty()) {
            log.warn("Post not found for postId: {}", event.getPostId());
            return;
        }

        Post post = postOptional.get();

        if (!event.isSuccess() || event.getMediaList() == null || event.getMediaList().isEmpty()) {
            if (post.getStatus() == PostStatus.PROCESSING) {
                post.setStatus(PostStatus.FAILED);
                postRepository.save(post);
            }
            postCacheEvictor.evictAllWithRecheck();
            log.warn("Post {} media upload marked as FAILED", post.getId());
            return;
        }

        for (PostMediaUploadedEvent.MediaItemPayload item : event.getMediaList()) {
            MediaType mediaType = MediaType.OTHER;
            try {
                mediaType = MediaType.valueOf(item.getMediaType());
            } catch (Exception ignored) {
            }

            PostMedia postMedia = PostMedia.builder()
                    .postId(post.getId())
                    .fileUrl(item.getFileUrl())
                    .fileKey(item.getFileKey())
                    .mediaType(mediaType)
                    .fileSize(item.getFileSize())
                    .sortOrder(item.getSortOrder())
                    .build();

            postMediaRepository.save(postMedia);
        }

        // Không được ghi đè trạng thái của bài đã bị từ chối, tạm ẩn hoặc đang chờ duyệt nhóm
        boolean becamePublished = false;
        if (!post.isArchived()
                && post.getModerationAction() == null
                && post.getStatus() != PostStatus.PENDING_APPROVAL
                && post.getStatus() != PostStatus.REJECTED) {
            if (post.getScheduledPublishAt() != null && post.getScheduledPublishAt().isAfter(Instant.now())) {
                post.setStatus(PostStatus.SCHEDULED);
            } else {
                becamePublished = post.getStatus() != PostStatus.PUBLISHED;
                post.setStatus(PostStatus.PUBLISHED);
                post.setLastActivityAt(LocalDateTime.now());
            }
        }

        postRepository.save(post);
        postCacheEvictor.evictAllWithRecheck();

        if (becamePublished) {
            postPublicationHelper.notifyPostPublished(post);
        }

        log.info("Post {} media processing completed with status {} and {} media items",
                post.getId(), post.getStatus(), event.getMediaList().size());
    }
}
