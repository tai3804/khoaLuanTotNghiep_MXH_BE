package iuh.fit.authservice.application.features.auth.commands.reset_password;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResetPasswordCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;

    @Transactional
    public void handle(ResetPasswordCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        String otpRedisKey = "password_reset:" + command.getEmail();
        String storedOtp = redisCacheService.get(otpRedisKey, String.class);

        if (storedOtp == null || !storedOtp.equals(command.getResetToken())) {
            log.warn("Invalid or expired OTP reset attempt for email: {}", command.getEmail());
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        // 1. Encode & update new password
        user.setPassword(passwordEncoder.encode(command.getNewPassword()));

        // 2. Increment tokenVersion in DB
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        // 3. Delete OTP from Redis
        redisCacheService.delete(otpRedisKey);

        // 4. Delete all device sessions
        deviceRepository.deleteAllByUserId(user.getId());

        // 5. Cache new tokenVersion in Redis with 1-hour TTL via RedisCacheService
        redisCacheService.setTokenVersion(user.getId(), newVersion, Duration.ofHours(1));

        log.info("Password successfully reset for email: {}, new tokenVersion: {}", command.getEmail(), newVersion);
    }
}
