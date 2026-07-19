package iuh.fit.authservice.application.features.auth.commands.change_password;

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
public class ChangePasswordCommandHandler {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Transactional
    public void handle(ChangePasswordCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(command.getOldPassword(), user.getPassword())) {
            log.warn("Failed password change attempt for user: {}", user.getId());
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(command.getNewPassword()));
        userRepository.save(user);
        
        log.info("Password successfully changed for user: {}", user.getId());
    }
}
