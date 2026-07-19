package iuh.fit.authservice.infrastructure.security;

import iuh.fit.authservice.domain.entities.User;

public interface TokenProvider {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
}
