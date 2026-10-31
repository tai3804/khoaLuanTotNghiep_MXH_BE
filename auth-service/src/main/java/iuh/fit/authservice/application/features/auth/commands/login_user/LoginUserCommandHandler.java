package iuh.fit.authservice.application.features.auth.commands.login_user;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TokenProvider;
import iuh.fit.authservice.application.mapper.LoginUserMapper;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.commonframework.infrastructure.security.OtpUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LoginUserCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    TokenProvider tokenProvider;
    JwtUtil jwtUtil;
    LoginUserMapper loginUserMapper;
    OtpUtil otpUtil;
    RedisCacheService redisCacheService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${AUTH_MAX_FAILED_LOGIN_ATTEMPTS:5}")
    int maxFailedLoginAttempts;

    @NonFinal
    @Value("${AUTH_FAILED_LOGIN_LOCKOUT_MINUTES:15}")
    int failedLoginLockoutMinutes;

    @Transactional
    public LoginUserResult handle(LoginUserCommand command) {
        String rawIdentifier = command.getEmail() != null ? command.getEmail().trim() : "";
        String normalizedEmail = rawIdentifier.toLowerCase();
        String failedKey = "auth:login_failed:" + normalizedEmail;

        int maxAttempts = maxFailedLoginAttempts > 0 ? maxFailedLoginAttempts : 5;
        Duration lockoutDuration = Duration.ofMinutes(failedLoginLockoutMinutes > 0 ? failedLoginLockoutMinutes : 15);

        // 1. Kiểm tra tài khoản có đang bị khóa tạm thời do nhập sai quá số lần không
        try {
            Integer failedCount = redisCacheService.get(failedKey, Integer.class);
            if (failedCount != null && failedCount >= maxAttempts) {
                log.warn("[BruteForceGuard] Chặn đăng nhập do vượt quá số lần thử: email={}, count={}, max={}",
                        normalizedEmail, failedCount, maxAttempts);
                throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Redis error checking failed login attempts: {}", e.getMessage());
        }

        User user = userRepository.findByEmail(rawIdentifier)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (UserStatus.BANNED.equals(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_BANNED);
        }

        if (UserStatus.LOCKED.equals(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }

        // 2. Xác thực mật khẩu
        if (!passwordEncoder.matches(command.getPassword(), user.getPassword())) {
            try {
                Long attempts = redisCacheService.increment(failedKey, 1);
                if (attempts != null && attempts == 1) {
                    redisCacheService.set(failedKey, 1, lockoutDuration);
                }
                if (attempts != null && attempts >= maxAttempts) {
                    log.warn("[BruteForceGuard] Khóa tạm thời tài khoản {} trong {} phút do sai mật khẩu {} lần",
                            normalizedEmail, lockoutDuration.toMinutes(), maxAttempts);
                    throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.warn("Redis error tracking failed login: {}", e.getMessage());
            }
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Đăng nhập thành công -> Xóa bộ đếm nhập sai
        try {
            redisCacheService.delete(failedKey);
        } catch (Exception ignored) {}

        if (user.isMfaEnabled()) {
            return processMfaLogin(user);
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        String fingerprint = (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank())
                ? command.getDeviceFingerprint().trim()
                : "default-web-device";
        command.setDeviceFingerprint(fingerprint);

        Device device = deviceRepository.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint)
                .orElseGet(() -> {
                    Device newDevice = new Device();
                    newDevice.setUser(user);
                    newDevice.setDeviceFingerprint(fingerprint);
                    return newDevice;
                });

        loginUserMapper.updateDevice(command, jwtUtil.hashToken(refreshToken), device);
        deviceRepository.save(device);

        return loginUserMapper.toLoginUserResult(accessToken, refreshToken, loginUserMapper.toDto(user));
    }

    private LoginUserResult processMfaLogin(User user) {
        String mfaToken = UUID.randomUUID().toString();
        String redisKey = "mfa:token:" + mfaToken;

        String code = null;
        if (MfaType.EMAIL.equals(user.getMfaType())) {
            code = otpUtil.generateOtp();
        }

        Map<String, String> mfaData = Map.of(
                "userId", user.getId().toString(),
                "mfaType", user.getMfaType().name(),
                "code", code != null ? code : ""
        );

        try {
            redisCacheService.set(redisKey, mfaData, otpUtil.getDefaultTtl());
        } catch (Exception e) {
            log.error("Redis Connection Error when processing MFA login: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        if (MfaType.EMAIL.equals(user.getMfaType())) {
            try {
                kafkaTemplate.send("notification.email.mfa-otp", Map.of(
                        "email", user.getEmail(),
                        "otp", code,
                        "firstName", user.getFirstName() != null ? user.getFirstName() : ""
                ));
            } catch (Exception e) {
                log.warn("Failed to publish MFA OTP event to Kafka: {}", e.getMessage());
            }
        }

        return LoginUserResult.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .mfaType(user.getMfaType())
                .build();
    }
}
