package iuh.fit.commonframework.infrastructure.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisCacheService {

    RedisTemplate<String, Object> redisTemplate;

    public static final String TOKEN_VERSION_PREFIX = "auth-service:user:token_version:";

    /** Thời gian tối đa tin vào phiên bản đã đọc trước đó khi Redis lỗi (bằng thời hạn access token). */
    private static final long FALLBACK_MAX_AGE_MS = Duration.ofMinutes(15).toMillis();
    private static final int FALLBACK_MAX_ENTRIES = 50_000;

    ConcurrentHashMap<UUID, VersionSnapshot> lastKnownVersions = new ConcurrentHashMap<>();

    @NonFinal
    @Value("${app.security.token-revocation.fail-open:false}")
    boolean failOpen;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void set(String key, Object value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null;
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public void hDelete(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    public void deletePattern(String pattern) {
        try {
            List<String> keysToDelete = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(pattern).count(200).build())) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception scanEx) {
            try {
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.error("Error deleting pattern [{}] from Redis: {}", pattern, e.getMessage());
            }
        }
    }

    // Token Versioning & Revocation
    public void setTokenVersion(UUID userId, int version, Duration timeout) {
        if (userId == null)
            return;
        String key = TOKEN_VERSION_PREFIX + userId;
        set(key, version, timeout);
    }

    public Integer getTokenVersion(UUID userId) {
        if (userId == null)
            return null;
        String key = TOKEN_VERSION_PREFIX + userId;
        Object val = get(key);
        if (val instanceof Integer) {
            return (Integer) val;
        } else if (val != null) {
            try {
                return Integer.parseInt(val.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Kiểm tra token đã bị thu hồi hay chưa.
     * <p>
     * Bình thường: so sánh tokenVersion của token với giá trị trong Redis.
     * Khi Redis lỗi (fail-closed theo mặc định):
     * <ol>
     * <li>Nếu đã đọc được phiên bản của user này trong vòng {@link #FALLBACK_MAX_AGE_MS}
     * (bằng thời hạn access token) thì dùng giá trị đó.</li>
     * <li>Nếu chưa có dữ liệu gần đây thì coi token là đã bị thu hồi để không chấp nhận
     * nhầm token của tài khoản bị khóa. Đặt app.security.token-revocation.fail-open=true
     * để quay về hành vi cũ (bỏ qua kiểm tra khi Redis lỗi).</li>
     * </ol>
     */
    public boolean isTokenRevoked(UUID userId, int tokenVersion) {
        try {
            Integer cachedVersion = getTokenVersion(userId);
            rememberTokenVersion(userId, cachedVersion);
            return cachedVersion != null && tokenVersion < cachedVersion;
        } catch (Exception e) {
            return isRevokedWhenRedisUnavailable(userId, tokenVersion, e);
        }
    }

    private void rememberTokenVersion(UUID userId, Integer version) {
        if (userId == null) {
            return;
        }
        if (lastKnownVersions.size() >= FALLBACK_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            lastKnownVersions.entrySet().removeIf(en -> now - en.getValue().at() > FALLBACK_MAX_AGE_MS);
            if (lastKnownVersions.size() >= FALLBACK_MAX_ENTRIES) {
                lastKnownVersions.clear();
            }
        }
        lastKnownVersions.put(userId, new VersionSnapshot(version == null ? 0 : version, System.currentTimeMillis()));
    }

    private boolean isRevokedWhenRedisUnavailable(UUID userId, int tokenVersion, Exception cause) {
        VersionSnapshot snapshot = userId == null ? null : lastKnownVersions.get(userId);
        if (snapshot != null && System.currentTimeMillis() - snapshot.at() <= FALLBACK_MAX_AGE_MS) {
            log.warn("Redis unavailable, using last known tokenVersion for user {}: {}", userId, cause.getMessage());
            return tokenVersion < snapshot.version();
        }
        if (failOpen) {
            log.error("Redis unavailable, token revocation check skipped (fail-open): {}", cause.getMessage());
            return false;
        }
        log.error("Redis unavailable and no recent tokenVersion for user {}, rejecting token (fail-closed): {}",
                userId, cause.getMessage());
        return true;
    }

    private record VersionSnapshot(int version, long at) {
    }
}
