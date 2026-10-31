package iuh.fit.postservice.infrastructure.task;

import iuh.fit.postservice.application.service.PostPublicationHelper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostSchedulerTask {

    PostRepository postRepository;
    PostCacheEvictor postCacheEvictor;
    PostPublicationHelper postPublicationHelper;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void publishScheduledPosts() {
        Instant now = Instant.now();
        List<Post> scheduledPosts = postRepository.findByStatusAndScheduledPublishAtLessThanEqualAndDeletedFalse(
                PostStatus.SCHEDULED, now
        );

        if (!scheduledPosts.isEmpty()) {
            log.info("Found {} scheduled posts due for publication", scheduledPosts.size());
            for (Post post : scheduledPosts) {
                post.setStatus(PostStatus.PUBLISHED);
                post.setLastActivityAt(LocalDateTime.now());
                Post saved = postRepository.save(post);
                postPublicationHelper.publishAndNotifyDeferredPost(saved);
                log.info("Successfully published scheduled post ID: {}", saved.getId());
            }

            postCacheEvictor.evictAllWithRecheck();
        }
    }
}
