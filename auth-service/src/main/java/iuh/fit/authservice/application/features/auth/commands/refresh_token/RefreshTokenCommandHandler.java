package iuh.fit.authservice.application.features.auth.commands.refresh_token;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    TokenProvider tokenProvider;
    JwtDecoder jwtDecoder;
    JwtUtil jwtUtil;

    @Transactional
    public RefreshTokenResult handle(RefreshTokenCommand command) {
        String tokenStr = command.getRefreshToken();
        if (tokenStr == null || tokenStr.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(tokenStr);
        } catch (JwtException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String type = jwt.getClaim("type");
        if (!"refresh".equals(type)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String userIdStr = jwt.getSubject();
        if (userIdStr == null) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        // Validate tokenVersion
        Long jwtTokenVersionLong = jwt.getClaim("tokenVersion");
        int jwtTokenVersion = jwtTokenVersionLong != null ? jwtTokenVersionLong.intValue() : 1;
        int currentTokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 1;

        if (jwtTokenVersion != currentTokenVersion) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);

        if (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank()) {
            deviceRepository.findByUserIdAndDeviceFingerprint(userId, command.getDeviceFingerprint())
                    .ifPresent(device -> {
                        device.setRefreshTokenHash(jwtUtil.hashToken(newRefreshToken));
                        deviceRepository.save(device);
                    });
        }

        return RefreshTokenResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
