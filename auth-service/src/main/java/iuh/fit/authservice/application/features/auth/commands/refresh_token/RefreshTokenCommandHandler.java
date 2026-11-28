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
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    TokenProvider tokenProvider;
    JwtDecoder jwtDecoder;
    JwtUtil jwtUtil;
    RedisCacheService redisCacheService;

    /**
     * Thời gian ân hạn sau mỗi lần xoay vòng: yêu cầu làm mới trùng (hai tab, mạng gửi lặp, nhiều nơi cùng gọi) mang refresh
     * token vừa bị thay được trả lại đúng cặp token mới thay vì bị coi là đánh cắp token và thu hồi thiết bị (đăng xuất).
     */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    static final String ROTATION_GRACE_PREFIX = "auth-service:refresh:rotated:";

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

        // Khóa dòng thiết bị đến hết giao dịch: hai yêu cầu làm mới đồng thời của cùng thiết bị được xử lý lần lượt,
        // yêu cầu sau thấy kết quả xoay vòng của yêu cầu trước (không cùng đọc một mã băm cũ rồi cùng xoay vòng).
        Device device = deviceRepository.findByIdForUpdate(deviceOpt.get().getId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));
        if (device.getStatus() == DeviceStatus.REVOKED) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        // Token Rotation check: Validate that the incoming refresh token matches the recorded hash
        if (device.getRefreshTokenHash() == null || !device.getRefreshTokenHash().equals(incomingTokenHash)) {
            // Refresh token vừa bị thay trong vài giây trước: đây là yêu cầu trùng, trả lại đúng refresh token mới đã cấp
            String rotatedTo = readRotation(device.getId(), incomingTokenHash);
            if (rotatedTo != null && device.getRefreshTokenHash() != null
                    && device.getRefreshTokenHash().equals(jwtUtil.hashToken(rotatedTo))) {
                log.info("Duplicate refresh within grace period for device {}: returning the already rotated token", device.getId());
                return RefreshTokenResult.builder()
                        .accessToken(tokenProvider.generateAccessToken(user, device.getId()))
                        .refreshToken(rotatedTo)
                        .build();
            }
            // Token reuse / replay attack detected! Revoke device immediately and persist without rollback
            device.setStatus(DeviceStatus.REVOKED);
            device.setRefreshTokenHash(null);
            deviceRepository.save(device);
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = tokenProvider.generateAccessToken(user, device.getId());
        String newRefreshToken = tokenProvider.generateRefreshToken(user, device.getId());

        device.setRefreshTokenHash(jwtUtil.hashToken(newRefreshToken));
        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);
        rememberRotation(device.getId(), incomingTokenHash, newRefreshToken);

        return RefreshTokenResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private void rememberRotation(UUID deviceId, String oldTokenHash, String newRefreshToken) {
        try {
            redisCacheService.set(ROTATION_GRACE_PREFIX + deviceId + ":" + oldTokenHash, newRefreshToken, ROTATION_GRACE);
        } catch (Exception e) {
            log.warn("Could not remember refresh rotation for device {}: {}", deviceId, e.getMessage());
        }
    }

    private String readRotation(UUID deviceId, String oldTokenHash) {
        try {
            return redisCacheService.get(ROTATION_GRACE_PREFIX + deviceId + ":" + oldTokenHash, String.class);
        } catch (Exception e) {
            log.warn("Could not read refresh rotation for device {}: {}", deviceId, e.getMessage());
            return null;
        }
    }
}
