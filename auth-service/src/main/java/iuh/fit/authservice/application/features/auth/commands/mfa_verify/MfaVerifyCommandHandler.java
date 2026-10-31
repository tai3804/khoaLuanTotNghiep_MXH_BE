package iuh.fit.authservice.application.features.auth.commands.mfa_verify;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.application.mapper.LoginUserMapper;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.authservice.infrastructure.security.TotpUtil;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaVerifyCommandHandler {

    private static final int MAX_MFA_ATTEMPTS = 5;

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    TokenProvider tokenProvider;
    LoginUserMapper loginUserMapper;
    JwtUtil jwtUtil;
    TotpUtil totpUtil;
    RedisCacheService redisCacheService;

    @Transactional
    public MfaVerifyResult handle(MfaVerifyCommand command) {
        String mfaToken = command.getMfaToken();
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        mfaToken = mfaToken.trim();

        String userIdStr = null;
        try {
            userIdStr = redisCacheService.get("mfa_session:" + mfaToken, String.class);
        } catch (Exception e) {
            log.error("Failed to read mfa_session from Redis: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        if (userIdStr == null || userIdStr.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (UserStatus.BANNED.equals(user.getStatus())) {
            evictMfaSession(mfaToken);
            throw new BusinessException(AuthErrorCode.ACCOUNT_BANNED);
        }

        if (UserStatus.LOCKED.equals(user.getStatus())) {
            evictMfaSession(mfaToken);
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }

        String mfaTypeStr = null;
        try {
            mfaTypeStr = redisCacheService.get("mfa_session_type:" + mfaToken, String.class);
        } catch (Exception ignored) {
        }
        MfaType mfaType = mfaTypeStr != null ? MfaType.valueOf(mfaTypeStr) : user.getMfaType();

        String providedCode = command.getOtpCode() != null ? command.getOtpCode().trim() : "";

        if (MfaType.TOTP == mfaType) {
            String secretKey = user.getMfaSecret();
            if (secretKey == null || secretKey.isBlank()) {
                try {
                    secretKey = redisCacheService.get("mfa_setup_secret:" + user.getId(), String.class);
                } catch (Exception ignored) {
                }
            }
            if (secretKey == null || secretKey.isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }
            if (!totpUtil.verifyCode(secretKey, providedCode)) {
                recordFailedMfaAttempt(mfaToken);
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }
        } else if (MfaType.EMAIL == mfaType) {
            String storedOtp = null;
            try {
                storedOtp = redisCacheService.get("mfa_email_otp:" + mfaToken, String.class);
            } catch (Exception ignored) {
            }
            if (storedOtp == null || !storedOtp.equals(providedCode)) {
                recordFailedMfaAttempt(mfaToken);
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }
        } else {
            evictMfaSession(mfaToken);
            throw new BusinessException(AuthErrorCode.INVALID_MFA_TYPE);
        }

        // Evict temporary MFA session from Redis
        evictMfaSession(mfaToken);

        // Issue final Access & Refresh Tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        String fingerprint = (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank())
                ? command.getDeviceFingerprint().trim()
                : "default-web-device";

        Device device = deviceRepository.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint)
                .orElseGet(() -> {
                    Device newDevice = new Device();
                    newDevice.setUser(user);
                    newDevice.setDeviceFingerprint(fingerprint);
                    return newDevice;
                });

        device.setStatus(DeviceStatus.ACTIVE);
        device.setLastActiveAt(LocalDateTime.now());
        device.setRefreshTokenHash(jwtUtil.hashToken(refreshToken));
        if (command.getDeviceName() != null && !command.getDeviceName().isBlank()) {
            device.setDeviceName(command.getDeviceName());
        }
        if (command.getIpAddress() != null && !command.getIpAddress().isBlank()) {
            device.setIpAddress(command.getIpAddress());
        }
        deviceRepository.save(device);

        return MfaVerifyResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(loginUserMapper.toDto(user))
                .build();
    }

    private void recordFailedMfaAttempt(String mfaToken) {
        String attemptsKey = "mfa_attempts:" + mfaToken;
        try {
            Integer current = redisCacheService.get(attemptsKey, Integer.class);
            int attempts = (current != null ? current : 0) + 1;
            redisCacheService.set(attemptsKey, attempts, Duration.ofMinutes(5));
            if (attempts >= MAX_MFA_ATTEMPTS) {
                evictMfaSession(mfaToken);
                throw new BusinessException(AuthErrorCode.TOO_MANY_ATTEMPTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }

    private void evictMfaSession(String mfaToken) {
        try {
            redisCacheService.delete("mfa_session:" + mfaToken);
            redisCacheService.delete("mfa_session_type:" + mfaToken);
            redisCacheService.delete("mfa_email_otp:" + mfaToken);
            redisCacheService.delete("mfa_attempts:" + mfaToken);
        } catch (Exception ignored) {
        }
    }
}
