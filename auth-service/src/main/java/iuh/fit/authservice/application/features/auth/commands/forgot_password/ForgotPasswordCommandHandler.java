package iuh.fit.authservice.application.features.auth.commands.forgot_password;

import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ForgotPasswordCommandHandler {

    UserRepository userRepository;

    public void handle(ForgotPasswordCommand command) {
        userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        // Mock generating an OTP/Reset token and sending an email
        String mockResetToken = UUID.randomUUID().toString();
        log.info("MOCK EMAIL SENDER: Password reset requested for {}. Token: {}", command.getEmail(), mockResetToken);
        
        // In a real scenario, you'd save this mockResetToken to the DB associated with the user 
        // with an expiration time, and send it via an EmailService.
    }
}
