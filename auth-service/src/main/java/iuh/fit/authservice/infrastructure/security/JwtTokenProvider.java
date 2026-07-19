package iuh.fit.authservice.infrastructure.security;

import iuh.fit.authservice.domain.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements TokenProvider {

    private final JwtEncoder jwtEncoder;

    @Value("${app.security.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${app.security.jwt.expiration.access-token}")
    private long accessTokenExpiration;

    @Value("${app.security.jwt.expiration.refresh-token}")
    private long refreshTokenExpiration;

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(now)
                .expiresAt(now.plusMillis(accessTokenExpiration))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Override
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(now)
                .expiresAt(now.plusMillis(refreshTokenExpiration))
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
