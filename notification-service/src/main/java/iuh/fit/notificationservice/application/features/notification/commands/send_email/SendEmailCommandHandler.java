package iuh.fit.notificationservice.application.features.notification.commands.send_email;

import iuh.fit.notificationservice.infrastructure.mail.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendEmailCommandHandler {

    EmailService emailService;

    public void handle(SendEmailCommand command) {
        emailService.sendPasswordResetEmail(command.getToEmail(), command.getResetToken());
    }
}
