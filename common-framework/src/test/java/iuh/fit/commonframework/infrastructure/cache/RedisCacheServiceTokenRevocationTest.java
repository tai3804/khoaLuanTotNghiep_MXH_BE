package iuh.fit.commonframework.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCacheServiceTokenRevocationTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;
    private RedisCacheService service;
    private final UUID userId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RedisCacheService(redisTemplate);
    }

    private void redisDown() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
    }

    @Test
    void oldTokenIsRevokedWhenRedisHasNewerVersion() {
        when(valueOps.get(anyString())).thenReturn(3);
        assertTrue(service.isTokenRevoked(userId, 2));
        assertFalse(service.isTokenRevoked(userId, 3));
    }

    @Test
    void tokenIsValidWhenNoVersionStored() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertFalse(service.isTokenRevoked(userId, 1));
    }

    @Test
    void redisDownFallsBackToLastKnownVersion() {
        when(valueOps.get(anyString())).thenReturn(3);
        service.isTokenRevoked(userId, 3);

        redisDown();
        assertTrue(service.isTokenRevoked(userId, 2), "token cũ vẫn bị chặn nhờ phiên bản đã biết");
        assertFalse(service.isTokenRevoked(userId, 3), "token hợp lệ vẫn dùng được");
    }

    @Test
    void redisDownWithoutHistoryRejectsTokenByDefault() {
        redisDown();
        assertTrue(service.isTokenRevoked(userId, 1));
    }

    @Test
    void redisDownWithoutHistoryAcceptsTokenWhenFailOpenEnabled() {
        ReflectionTestUtils.setField(service, "failOpen", true);
        redisDown();
        assertFalse(service.isTokenRevoked(userId, 1));
    }

    @Test
    void revokedDeviceIsReportedUntilFlagIsCleared() {
        UUID device = UUID.randomUUID();
        when(redisTemplate.hasKey(RedisCacheService.DEVICE_REVOKED_PREFIX + device)).thenReturn(true);
        assertTrue(service.isDeviceRevoked(device));
        when(redisTemplate.hasKey(RedisCacheService.DEVICE_REVOKED_PREFIX + device)).thenReturn(false);
        assertFalse(service.isDeviceRevoked(device));
    }

    @Test
    void deviceCheckDoesNotRejectWhenRedisIsDownOrDeviceIsNull() {
        UUID device = UUID.randomUUID();
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertFalse(service.isDeviceRevoked(device));
        assertFalse(service.isDeviceRevoked(null));
    }
}
