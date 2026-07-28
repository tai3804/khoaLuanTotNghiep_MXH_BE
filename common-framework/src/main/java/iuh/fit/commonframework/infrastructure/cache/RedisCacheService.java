package iuh.fit.commonframework.infrastructure.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisCacheService {

    RedisTemplate<String, Object> redisTemplate;

    static String TOKEN_VERSION_PREFIX = "auth-service:user:token_version:";

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
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Error deleting pattern [{}] from Redis: {}", pattern, e.getMessage());
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

    public boolean isTokenRevoked(UUID userId, int tokenVersion) {
        Integer cachedVersion = getTokenVersion(userId);
        return cachedVersion != null && tokenVersion < cachedVersion;
    }
}
