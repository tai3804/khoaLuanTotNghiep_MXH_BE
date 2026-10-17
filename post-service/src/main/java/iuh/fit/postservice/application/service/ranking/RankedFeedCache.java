package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Mode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Cache bản chụp bảng tin đã xếp hạng theo (người xem, chế độ). Hỏng Redis thì tính lại, không làm lỗi request. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankedFeedCache {

    public static final String CACHE_NAME = "post-rank-v1";

    private final CacheManager cacheManager;

    public Optional<RankedFeedSnapshot> get(UUID viewerId, Mode mode, boolean unseenOnly) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return Optional.empty();
        String key = key(viewerId, mode, unseenOnly);
        try {
            return Optional.ofNullable(cache.get(key, RankedFeedSnapshot.class));
        } catch (Exception e) {
            log.debug("Ranked feed cache read failed ({}): {}", key, e.getMessage());
            try {
                cache.evict(key);
            } catch (Exception ignored) {
                // bỏ qua: lần sau sẽ tính lại
            }
            return Optional.empty();
        }
    }

    public void put(UUID viewerId, Mode mode, boolean unseenOnly, RankedFeedSnapshot snapshot) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return;
        String key = key(viewerId, mode, unseenOnly);
        try {
            cache.put(key, snapshot);
        } catch (Exception e) {
            log.debug("Ranked feed cache write failed ({}): {}", key, e.getMessage());
        }
    }

    /** Bỏ bản chụp của một người (vd vừa đăng/xoá bài) để lần tải bảng tin kế tiếp thấy ngay thay đổi. */
    public void evictViewer(UUID viewerId) {
        if (viewerId == null) return;
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return;
        for (Mode mode : Mode.values()) {
            for (boolean unseenOnly : new boolean[]{false, true}) {
                try {
                    cache.evict(key(viewerId, mode, unseenOnly));
                } catch (Exception ignored) {
                    // bỏ qua: bản chụp tự hết hạn sau một thời gian
                }
            }
        }
    }

    /** Bản chụp "chỉ bài chưa xem" và bản chụp đầy đủ được lưu tách nhau. */
    private static String key(UUID viewerId, Mode mode, boolean unseenOnly) {
        return viewerId + ":" + mode.name() + (unseenOnly ? ":unseen" : "");
    }
}
