package iuh.fit.authservice.infrastructure.security;

import iuh.fit.authservice.domain.entities.User;

import java.util.UUID;

public interface TokenProvider {
    /** Token mang claim deviceId để thu hồi riêng từng thiết bị (đăng xuất từ xa) có hiệu lực ngay. */
    String generateAccessToken(User user, UUID deviceId);
    String generateRefreshToken(User user, UUID deviceId);

    default String generateAccessToken(User user) {
        return generateAccessToken(user, null);
    }

    default String generateRefreshToken(User user) {
        return generateRefreshToken(user, null);
    }
}
