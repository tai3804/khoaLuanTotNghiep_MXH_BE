package iuh.fit.authservice.application.features.auth.commands.login_user;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    LoginUserMapper loginUserMapper;
    JwtUtil jwtUtil;
    OtpUtil otpUtil;
    RedisCacheService redisCacheService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public LoginUserResult handle(LoginUserCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(command.getPassword(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.isMfaEnabled()) {
            return processMfaLogin(user);
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        Device device = deviceRepository.findByUserIdAndDeviceFingerprint(user.getId(), command.getDeviceFingerprint())
                .orElseGet(() -> {
                    Device newDevice = new Device();
                    newDevice.setUser(user);
                    newDevice.setDeviceFingerprint(command.getDeviceFingerprint());
                    return newDevice;
                });

        loginUserMapper.updateDevice(command, jwtUtil.hashToken(refreshToken), device);
        deviceRepository.save(device);

        return loginUserMapper.toLoginUserResult(accessToken, refreshToken, loginUserMapper.toDto(user));
    }

    private LoginUserResult processMfaLogin(User user) {
        String mfaToken = UUID.randomUUID().toString();
        MfaType mfaType = user.getMfaType() != null ? user.getMfaType() : MfaType.TOTP;

        redisCacheService.set("mfa_session:" + mfaToken, user.getId().toString(), otpUtil.getDefaultTtl());
        redisCacheService.set("mfa_session_type:" + mfaToken, mfaType.name(), otpUtil.getDefaultTtl());

        if (MfaType.EMAIL == mfaType) {
            String otp = otpUtil.generateOtp();
            redisCacheService.set("mfa_email_otp:" + mfaToken, otp, otpUtil.getDefaultTtl());

            try {
                kafkaTemplate.send("notification.email.password-reset", Map.of(
                        "email", user.getEmail(),
                        "otp", otp,
                        "firstName", user.getFirstName() != null ? user.getFirstName() : ""
                ));
                log.info("Sent MFA Email OTP to user {}: {}", user.getEmail(), otp);
            } catch (Exception e) {
                log.warn("Failed to publish MFA OTP notification to Kafka: {}", e.getMessage());
            }
        }

        return LoginUserResult.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .mfaType(mfaType)
                .build();
    }
}
