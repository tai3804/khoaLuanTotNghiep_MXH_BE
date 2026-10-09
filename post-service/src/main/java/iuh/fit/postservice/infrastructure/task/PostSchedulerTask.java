package iuh.fit.postservice.infrastructure.task;

import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostSchedulerTask {

    PostRepository postRepository;
    CacheManager cacheManager;

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
                postRepository.save(post);
                log.info("Successfully published scheduled post ID: {}", post.getId());
            }

            for (String cacheName : List.of("post-feed-v2", "post-user-feed-v2", "post-detail-v2")) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
        }
    }
}
