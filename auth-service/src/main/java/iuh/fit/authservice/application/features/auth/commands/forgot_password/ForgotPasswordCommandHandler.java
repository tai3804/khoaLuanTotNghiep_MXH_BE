package iuh.fit.authservice.application.features.auth.commands.forgot_password;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.OtpUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ForgotPasswordCommandHandler {

    UserRepository userRepository;
    RedisCacheService redisCacheService;
    OtpUtil otpUtil;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${AUTH_OTP_COOLDOWN_SECONDS:60}")
    int otpCooldownSeconds;

    public void handle(ForgotPasswordCommand command) {
        String emailInput = command.getEmail() != null ? command.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(emailInput)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        String normalizedEmail = user.getEmail().trim().toLowerCase();

        String cooldownKey = "password_reset_cooldown:" + normalizedEmail;
        try {
            if (redisCacheService.hasKey(cooldownKey)) {
                throw new BusinessException(AuthErrorCode.OTP_REQUEST_COOLDOWN);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Redis check reset cooldown error: {}", e.getMessage());
        }

        String otp = otpUtil.generateOtp();
        String redisKey = "password_reset:" + normalizedEmail;
        String attemptsKey = "password_reset_attempts:" + normalizedEmail;

        try {
            redisCacheService.set(redisKey, otp, otpUtil.getResetPasswordTtl());
            redisCacheService.delete(attemptsKey);
            redisCacheService.set(cooldownKey, "1", Duration.ofSeconds(otpCooldownSeconds > 0 ? otpCooldownSeconds : 60));
        } catch (Exception e) {
            log.error("Failed to store password reset OTP in Redis: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }
        log.info("Generated password reset OTP for userId {}", user.getId());

        try {
            kafkaTemplate.send("notification.email.password-reset", Map.of(
                    "email", user.getEmail(),
                    "otp", otp,
                    "firstName", user.getFirstName() != null ? user.getFirstName() : ""
            ));
        } catch (Exception e) {
            log.warn("Failed to publish password reset notification event to Kafka: {}", e.getMessage());
        }
    }
}
