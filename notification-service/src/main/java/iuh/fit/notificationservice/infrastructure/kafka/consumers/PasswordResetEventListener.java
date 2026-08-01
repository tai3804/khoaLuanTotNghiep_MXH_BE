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
public class PasswordResetEventListener {

    EmailService emailService;

    @KafkaListener(topics = "notification.email.password-reset", groupId = "notification-service-group")
    public void handlePasswordResetEvent(Map<String, Object> event) {
        String email = (String) event.get("email");
        String otp = (String) event.get("otp");

        log.info("Received PasswordResetEvent from Kafka for email: {}", email);

        if (email != null && otp != null) {
            try {
                emailService.sendPasswordResetEmail(email, otp);
                log.info("Successfully processed password reset email notification for {}", email);
            } catch (Exception e) {
                log.error("Error processing password reset email notification for {}: {}", email, e.getMessage());
            }
        } else {
            log.warn("Invalid payload received for password reset event: {}", event);
        }
    }
}
