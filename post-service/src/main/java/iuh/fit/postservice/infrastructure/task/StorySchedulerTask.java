package iuh.fit.postservice.infrastructure.task;

import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Facebook-style automated 24h story lifecycle scheduler:
 * - Scans every 60s for stories past their 24-hour expiration (expiresAt <= now).
 * - Automatically transitions expired stories to the user's private "Kho lưu trữ tin" (Story Archive).
 * - Automatically removes them from active story trays across the platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorySchedulerTask {

    StoryRepository storyRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupStoryCleanup() {
        try {
            int nullCount = storyRepository.archiveNullExpiryStories();
            int expiredCount = storyRepository.archiveExpiredStories(Instant.now());
            if (nullCount > 0 || expiredCount > 0) {
                log.info("[StoryLifecycle] Startup cleanup: archived {} null-expiry stories and {} past-due 24h stories.", nullCount, expiredCount);
            }
        } catch (Exception e) {
            log.warn("[StoryLifecycle] Startup story archive cleanup encountered non-critical error: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void archiveExpiredStories() {
        try {
            Instant now = Instant.now();
            int count = storyRepository.archiveExpiredStories(now);
            if (count > 0) {
                log.info("[StoryLifecycle] Automatically archived {} expired stories (24h lifespan elapsed).", count);
            }
        } catch (Exception e) {
            log.error("[StoryLifecycle] Scheduled story archival error: {}", e.getMessage(), e);
        }
    }
}
