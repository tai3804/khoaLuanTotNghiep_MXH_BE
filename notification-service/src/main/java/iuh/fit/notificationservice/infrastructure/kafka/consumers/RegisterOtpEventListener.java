package iuh.fit.notificationservice.infrastructure.kafka.consumers;

import iuh.fit.notificationservice.infrastructure.mail.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegisterOtpEventListener {

    EmailService emailService;

    @KafkaListener(topics = "notification.email.register-otp", groupId = "notification-service-group")
    public void handleRegisterOtpEvent(Map<String, Object> event) {
        String email = (String) event.get("email");
        String otp = (String) event.get("otp");

        log.info("Received RegisterOtpEvent from Kafka for email: {}", email);

        if (email != null && otp != null) {
            try {
                emailService.sendRegistrationOtpEmail(email, otp);
                log.info("Successfully processed registration OTP email notification for {}", email);
            } catch (Exception e) {
                log.error("Error processing registration OTP email notification for {}: {}", email, e.getMessage());
            }
        } else {
            log.warn("Invalid payload received for registration OTP event: {}", event);
        }
    }
}
