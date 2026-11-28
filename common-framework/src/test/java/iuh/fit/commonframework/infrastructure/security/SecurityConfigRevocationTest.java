package iuh.fit.commonframework.infrastructure.security;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigRevocationTest {

    private static Jwt jwt(String subject, Object tokenVersion) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tokenVersion != null) b.claim("tokenVersion", tokenVersion);
        return b.build();
    }

    @Test
    void revokedTokenThrowsJwtValidationExceptionWithNonEmptyErrors() {
        UUID user = UUID.randomUUID();
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.isTokenRevoked(user, 1)).thenReturn(true);

        JwtValidationException ex = assertThrows(JwtValidationException.class,
                () -> SecurityConfig.assertNotRevoked(jwt(user.toString(), 1), redis));
        assertFalse(ex.getErrors().isEmpty(), "danh sách lỗi rỗng sẽ làm Spring ném IllegalArgumentException và token bị chấp nhận");
        assertTrue(ex.getMessage().contains("revoked"));
    }

    @Test
    void validTokenPasses() {
        UUID user = UUID.randomUUID();
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.isTokenRevoked(user, 2)).thenReturn(false);
        SecurityConfig.assertNotRevoked(jwt(user.toString(), 2), redis);
    }

    @Test
    void tokenVersionAsStringIsParsed() {
        UUID user = UUID.randomUUID();
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.isTokenRevoked(user, 3)).thenReturn(true);
        assertThrows(JwtValidationException.class, () -> SecurityConfig.assertNotRevoked(jwt(user.toString(), "3"), redis));
    }

    @Test
    void nonUserSubjectIsIgnored() {
        RedisCacheService redis = mock(RedisCacheService.class);
        SecurityConfig.assertNotRevoked(jwt("service-account", 1), redis);
    }

    private static Jwt jwtWithDevice(String subject, UUID device) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("tokenVersion", 1).claim("deviceId", device.toString()).build();
    }

    @Test
    void tokenOfRevokedDeviceIsRejectedWhileOtherDeviceStillPasses() {
        UUID user = UUID.randomUUID(), revoked = UUID.randomUUID(), other = UUID.randomUUID();
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.isTokenRevoked(user, 1)).thenReturn(false);
        when(redis.isDeviceRevoked(revoked)).thenReturn(true);
        when(redis.isDeviceRevoked(other)).thenReturn(false);

        JwtValidationException ex = assertThrows(JwtValidationException.class,
                () -> SecurityConfig.assertNotRevoked(jwtWithDevice(user.toString(), revoked), redis));
        assertFalse(ex.getErrors().isEmpty());
        SecurityConfig.assertNotRevoked(jwtWithDevice(user.toString(), other), redis);
    }

    @Test
    void tokenWithoutDeviceClaimIsNotAffectedByDeviceRevocation() {
        UUID user = UUID.randomUUID();
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.isTokenRevoked(user, 1)).thenReturn(false);
        SecurityConfig.assertNotRevoked(jwt(user.toString(), 1), redis);
    }
}
