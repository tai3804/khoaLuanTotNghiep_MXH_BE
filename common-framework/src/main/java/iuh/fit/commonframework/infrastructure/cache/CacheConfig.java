package iuh.fit.commonframework.infrastructure.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

/**
 * Cấu hình Redis Cache.
 */
@Configuration
@EnableCaching
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheConfig {

    @Value("${spring.cache.redis.time-to-live}")
    Duration timeToLive;

    /**
     * Tùy chỉnh RedisCacheManager với JSON serialization và TTL động.
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(timeToLive)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.json()));
    }
}
