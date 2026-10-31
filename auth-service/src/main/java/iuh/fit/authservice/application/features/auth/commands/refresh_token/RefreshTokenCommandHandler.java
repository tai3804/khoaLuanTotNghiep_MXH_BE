package iuh.fit.authservice.application.features.auth.commands.refresh_token;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.enums.UserStatus;
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

import java.time.LocalDateTime;
import java.util.Optional;
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

    @Transactional(noRollbackFor = BusinessException.class)
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

        if (UserStatus.BANNED.equals(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_BANNED);
        }

        if (UserStatus.LOCKED.equals(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }

        int jwtTokenVersion = jwtUtil.extractTokenVersion(jwt);
        int currentTokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 1;

        if (jwtTokenVersion != currentTokenVersion) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String incomingTokenHash = jwtUtil.hashToken(tokenStr);
        Optional<Device> deviceOpt;
        if (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank()) {
            deviceOpt = deviceRepository.findByUserIdAndDeviceFingerprint(userId, command.getDeviceFingerprint().trim());
        } else {
            deviceOpt = deviceRepository.findByUserIdAndRefreshTokenHash(userId, incomingTokenHash);
        }

        if (deviceOpt.isEmpty()) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Device device = deviceOpt.get();
        if (device.getStatus() == DeviceStatus.REVOKED) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        // Token Rotation check: Validate that the incoming refresh token matches the recorded hash
        if (device.getRefreshTokenHash() == null || !device.getRefreshTokenHash().equals(incomingTokenHash)) {
            // Token reuse / replay attack detected! Revoke device immediately and persist without rollback
            device.setStatus(DeviceStatus.REVOKED);
            device.setRefreshTokenHash(null);
            deviceRepository.save(device);
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);

        device.setRefreshTokenHash(jwtUtil.hashToken(newRefreshToken));
        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);

        return RefreshTokenResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
