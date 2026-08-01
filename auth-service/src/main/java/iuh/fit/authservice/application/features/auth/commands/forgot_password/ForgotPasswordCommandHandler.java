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
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

    public void handle(ForgotPasswordCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        String otp = otpUtil.generateOtp();
        String redisKey = "password_reset:" + user.getEmail();

        redisCacheService.set(redisKey, otp, otpUtil.getResetPasswordTtl());
        log.info("Generated Password Reset OTP for email {}: {}", user.getEmail(), otp);

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
