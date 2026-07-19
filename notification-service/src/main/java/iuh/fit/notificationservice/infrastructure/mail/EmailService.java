package iuh.fit.notificationservice.infrastructure.mail;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailService {

    @Value("${brevo.api.url}")
    String apiUrl;

    @Value("${brevo.api.key}")
    String apiKey;

    @Value("${brevo.sender.email}")
    String senderEmail;

    @Value("${brevo.sender.name}")
    String senderName;

    final RestTemplate restTemplate = new RestTemplate();

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("Sending password reset email to {} via Brevo", toEmail);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", "Password Reset Request",
                    "htmlContent", "<p>To reset your password, please use the following token: <strong>" + resetToken + "</strong></p><p>If you did not request this, please ignore this email.</p>"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Password reset email successfully sent to {}", toEmail);
            } else {
                log.error("Failed to send email to {}. Brevo response: {}", toEmail, response.getBody());
                throw new RuntimeException("Brevo API returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send email to {} via Brevo", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
