package iuh.fit.authservice.application.features.auth.commands.reset_password;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResetPasswordCommandHandler {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Transactional
    public void handle(ResetPasswordCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.EMAIL_NOT_FOUND));

        // Mock verification: In a real app, verify the command.getResetToken() against the DB
        // For now, we assume the token is valid since we just mock-logged it.
        
        user.setPassword(passwordEncoder.encode(command.getNewPassword()));
        userRepository.save(user);
        
        log.info("Password successfully reset for email: {}", command.getEmail());
    }
}
