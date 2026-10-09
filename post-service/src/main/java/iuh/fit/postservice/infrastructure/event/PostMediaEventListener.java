package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostMediaUploadedEvent;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostMediaEventListener {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;

    @Transactional
    @KafkaListener(topics = "post.media.uploaded", groupId = "post-group")
    public void handlePostMediaUploaded(PostMediaUploadedEvent event) {
        log.info("Received PostMediaUploadedEvent for postId: {}, success: {}", event.getPostId(), event.isSuccess());

        Optional<Post> postOptional = postRepository.findByIdAndDeletedFalse(event.getPostId());
        if (postOptional.isEmpty()) {
            log.warn("Post not found for postId: {}", event.getPostId());
            return;
        }

        Post post = postOptional.get();

        if (!event.isSuccess() || event.getMediaList() == null || event.getMediaList().isEmpty()) {
            post.setStatus(PostStatus.FAILED);
            postRepository.save(post);
            log.warn("Post {} media upload marked as FAILED", post.getId());
            return;
        }

        for (PostMediaUploadedEvent.MediaItemPayload item : event.getMediaList()) {
            MediaType mediaType = MediaType.OTHER;
            try {
                mediaType = MediaType.valueOf(item.getMediaType());
            } catch (Exception ignored) {}

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

        // Media processing must not bypass a group's approval queue. A post that
        // was awaiting review remains there after its files have been uploaded.
        if (post.getStatus() != PostStatus.PENDING_APPROVAL) {
            if (post.getScheduledPublishAt() != null && post.getScheduledPublishAt().isAfter(java.time.Instant.now())) {
                post.setStatus(PostStatus.SCHEDULED);
            } else {
                post.setStatus(PostStatus.PUBLISHED);
            }
        }
        postRepository.save(post);
        log.info("Post {} media processing completed with status {} and {} media items", post.getId(), post.getStatus(), event.getMediaList().size());
    }
}
