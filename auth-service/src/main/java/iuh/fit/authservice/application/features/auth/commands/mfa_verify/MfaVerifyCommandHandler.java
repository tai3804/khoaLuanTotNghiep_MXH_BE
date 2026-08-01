package iuh.fit.authservice.application.features.auth.commands.mfa_verify;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.application.mapper.LoginUserMapper;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaVerifyCommandHandler {

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

        String userIdStr = redisCacheService.get("mfa_session:" + mfaToken, String.class);
        if (userIdStr == null) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        String mfaTypeStr = redisCacheService.get("mfa_session_type:" + mfaToken, String.class);
        MfaType mfaType = mfaTypeStr != null ? MfaType.valueOf(mfaTypeStr) : user.getMfaType();

        if (MfaType.TOTP == mfaType) {
            if (!totpUtil.verifyCode(user.getMfaSecret(), command.getOtpCode())) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
        } else if (MfaType.EMAIL == mfaType) {
            String storedOtp = redisCacheService.get("mfa_email_otp:" + mfaToken, String.class);
            if (storedOtp == null || !storedOtp.equals(command.getOtpCode())) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
            redisCacheService.delete("mfa_email_otp:" + mfaToken);
        }

        // Evict temporary MFA session from Redis
        redisCacheService.delete("mfa_session:" + mfaToken);
        redisCacheService.delete("mfa_session_type:" + mfaToken);

        // Issue final Access & Refresh Tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        if (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank()) {
            Device device = deviceRepository.findByUserIdAndDeviceFingerprint(user.getId(), command.getDeviceFingerprint())
                    .orElseGet(() -> {
                        Device newDevice = new Device();
                        newDevice.setUser(user);
                        newDevice.setDeviceFingerprint(command.getDeviceFingerprint());
                        return newDevice;
                    });

            device.setRefreshTokenHash(jwtUtil.hashToken(refreshToken));
            deviceRepository.save(device);
        }

        return MfaVerifyResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(loginUserMapper.toDto(user))
                .build();
    }
}
