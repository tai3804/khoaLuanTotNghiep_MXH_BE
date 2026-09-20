package iuh.fit.postservice.infrastructure.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache does not reuse the RedisTemplate serializer automatically.
 * Explicit JSON serialization is required for PagedResponse and post DTOs.
 */
@Configuration
public class PostRedisCacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    /**
     * A cache entry is an optimisation, never a reason for the feed endpoint
     * to fail.  Schema changes can leave an old JSON value in Redis for a
     * short period, so evict an unreadable value and let Spring load from DB.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                try {
                    cache.evict(key);
                } catch (RuntimeException ignored) {
                    // Keep the request available even if Redis is unavailable.
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // The database response remains valid when Redis cannot cache it.
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // TTL prevents a temporary Redis failure from leaving stale data forever.
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                // Same graceful-degradation policy as other cache operations.
            }
        };
    }
}
