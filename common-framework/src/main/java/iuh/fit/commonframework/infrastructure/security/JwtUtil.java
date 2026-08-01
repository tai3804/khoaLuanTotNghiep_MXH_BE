package iuh.fit.commonframework.infrastructure.security;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    /**
     * Lấy User ID (subject) từ SecurityContext.
     */
    public String getCurrentUserId() {
        Jwt jwt = getJwtFromContext();
        if (jwt != null) {
            return jwt.getSubject();
        }
        return null;
    }

    /**
     * Lấy claim từ JWT trong SecurityContext.
     */
    public <T> T getClaim(String claimName) {
        Jwt jwt = getJwtFromContext();
        if (jwt != null) {
            return jwt.getClaim(claimName);
        }
        return null;
    }

    /**
     * Lấy tokenVersion từ JWT trong SecurityContext.
     */
    public Integer getTokenVersion() {
        Jwt jwt = getJwtFromContext();
        if (jwt != null && jwt.hasClaim("tokenVersion")) {
            Long versionLong = jwt.getClaim("tokenVersion");
            return versionLong != null ? versionLong.intValue() : 1;
        }
        return 1;
    }

    /**
     * Lấy JWT object từ SecurityContext.
     */
    public Jwt getJwtFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            return (Jwt) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * Lấy danh sách Roles / Authorities của User từ JWT
     */
    public List<String> getCurrentUserRoles() {
        Jwt jwt = getJwtFromContext();
        if (jwt != null && jwt.hasClaim("roles")) {
            return jwt.getClaimAsStringList("roles");
        }
        return Collections.emptyList();
    }

    /**
     * Kiểm tra Token hợp lệ thủ công (chưa hết hạn)
     */
    public boolean isTokenValid() {
        Jwt jwt = getJwtFromContext();
        if (jwt != null) {
            return jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now());
        }
        return false;
    }

    /**
     * Kiểm tra xem Token có bị thu hồi hay không qua RedisCacheService.
     */
    public boolean isTokenRevoked(RedisCacheService redisCacheService) {
        Jwt jwt = getJwtFromContext();
        if (jwt == null || redisCacheService == null) {
            return false;
        }

        String userIdStr = jwt.getSubject();
        if (userIdStr == null) {
            return false;
        }

        try {
            UUID userId = UUID.fromString(userIdStr);
            Integer version = getTokenVersion();
            return redisCacheService.isTokenRevoked(userId, version != null ? version : 1);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Kiểm tra xem Token có bị thu hồi hay không qua StringRedisTemplate.
     */
    public boolean isTokenRevoked(StringRedisTemplate redisTemplate) {
        Jwt jwt = getJwtFromContext();
        if (jwt == null || redisTemplate == null) {
            return false;
        }

        String userId = jwt.getSubject();
        if (userId == null) {
            return false;
        }

        String redisKey = "user:token_version:" + userId;
        String cachedVersionStr = redisTemplate.opsForValue().get(redisKey);

        if (cachedVersionStr != null) {
            try {
                int cachedVersion = Integer.parseInt(cachedVersionStr);
                Integer tokenVersion = getTokenVersion();
                if (tokenVersion != null && tokenVersion < cachedVersion) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return false;
    }

    /**
     * Mã hóa (băm) token bằng SHA-256
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
