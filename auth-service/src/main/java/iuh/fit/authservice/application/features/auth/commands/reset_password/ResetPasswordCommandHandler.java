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

    private static final int MAX_RESET_ATTEMPTS = 5;

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;

    @Transactional
    public void handle(ResetPasswordCommand command) {
        String normalizedEmail = command.getEmail() != null ? command.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        String canonicalEmail = user.getEmail().trim().toLowerCase();
        String otpRedisKey = "password_reset:" + canonicalEmail;
        String attemptsKey = "password_reset_attempts:" + canonicalEmail;
        String storedOtp = redisCacheService.get(otpRedisKey, String.class);

        if (storedOtp == null) {
            log.warn("Expired or missing OTP reset attempt for email: {}", canonicalEmail);
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        String providedOtp = command.getResetToken() != null ? command.getResetToken().trim() : "";
        if (!storedOtp.equals(providedOtp)) {
            Integer currentAttempts = redisCacheService.get(attemptsKey, Integer.class);
            int attempts = (currentAttempts != null ? currentAttempts : 0) + 1;
            redisCacheService.set(attemptsKey, attempts, Duration.ofMinutes(15));
            if (attempts >= MAX_RESET_ATTEMPTS) {
                redisCacheService.delete(otpRedisKey);
                redisCacheService.delete(attemptsKey);
                throw new BusinessException(AuthErrorCode.TOO_MANY_ATTEMPTS);
            }
            log.warn("Invalid OTP reset attempt ({}/{}) for email: {}", attempts, MAX_RESET_ATTEMPTS, canonicalEmail);
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        // 1. Encode & update new password
        user.setPassword(passwordEncoder.encode(command.getNewPassword()));

        // 2. Increment tokenVersion in DB
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        // 3. Delete OTP & attempt counter from Redis
        redisCacheService.delete(otpRedisKey);
        redisCacheService.delete(attemptsKey);

        // 4. Delete all device sessions
        deviceRepository.deleteAllByUserId(user.getId());

        // 5. Cache new tokenVersion in Redis with 7-day TTL via RedisCacheService
        redisCacheService.setTokenVersion(user.getId(), newVersion, Duration.ofDays(7));

        log.info("Password successfully reset for userId: {}, new tokenVersion: {}", user.getId(), newVersion);
    }
}
