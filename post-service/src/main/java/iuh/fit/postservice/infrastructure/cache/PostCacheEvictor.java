package iuh.fit.postservice.infrastructure.cache;

import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Xóa các cache bài viết ngoài luồng @CacheEvict (Kafka listener, controller quản trị...),
 * để thay đổi do kiểm duyệt có hiệu lực ngay thay vì chờ hết TTL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCacheEvictor {

    static final List<String> POST_CACHES = List.of("post-feed-v2", "post-user-feed-v2", "post-detail-v2");

    /** Đủ dài để các request đang đọc dở (bắt đầu trước khi thay đổi được commit) kịp ghi xong vào cache. */
    static final long RECHECK_DELAY_MS = 1500;

    CacheManager cacheManager;

    ScheduledExecutorService recheckExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "post-cache-recheck");
        thread.setDaemon(true);
        return thread;
    });

    public void evictAll() {
        for (String cacheName : POST_CACHES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    /**
     * Xóa ngay rồi xóa lại sau một nhịp ngắn. Một request đọc dữ liệu cũ ngay trước khi commit có thể ghi bản cũ vào cache
     * SAU lần xóa đầu và bản đó sẽ sống tới hết TTL; lần xóa thứ hai loại bỏ nó.
     */
    public void evictAllWithRecheck() {
        evictAll();
        recheckExecutor.schedule(() -> {
            try {
                evictAll();
            } catch (Exception e) {
                log.warn("Delayed post cache eviction failed: {}", e.getMessage());
            }
        }, RECHECK_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        recheckExecutor.shutdownNow();
    }
}
