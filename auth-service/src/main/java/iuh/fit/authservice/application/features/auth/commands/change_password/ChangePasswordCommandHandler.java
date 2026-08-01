package iuh.fit.authservice.application.features.auth.commands.change_password;

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
public class ChangePasswordCommandHandler {

    UserRepository userRepository;
    DeviceRepository deviceRepository;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;

    @Transactional
    public void handle(ChangePasswordCommand command) {
        if (command.getUserId() == null) {
            throw new BusinessException(AuthErrorCode.USER_NOT_FOUND);
        }

        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(command.getOldPassword(), user.getPassword())) {
            log.warn("Failed password change attempt for user: {}", user.getId());
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD);
        }

        // 1. Encode & update new password
        user.setPassword(passwordEncoder.encode(command.getNewPassword()));
        
        // 2. Increment tokenVersion in DB
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        // 3. Delete all device sessions
        deviceRepository.deleteAllByUserId(user.getId());

        // 4. Cache new tokenVersion in Redis with 1-hour TTL via RedisCacheService
        redisCacheService.setTokenVersion(user.getId(), newVersion, Duration.ofHours(1));

        log.info("Password successfully changed for user: {}, new tokenVersion: {}", user.getId(), newVersion);
    }
}
