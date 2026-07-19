package iuh.fit.commonframework.infrastructure.security;

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
     * Lấy JWT object từ SecurityContext.
     */
    private Jwt getJwtFromContext() {
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
