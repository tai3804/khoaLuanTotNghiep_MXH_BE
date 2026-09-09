package iuh.fit.notificationservice.application.features.fcm.commands.register_token;

import iuh.fit.notificationservice.domain.entities.UserFcmToken;
import iuh.fit.notificationservice.domain.repositories.UserFcmTokenRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegisterFcmTokenHandler {

    UserFcmTokenRepository tokenRepository;

    @Transactional
    public UserFcmToken handle(RegisterFcmTokenCommand command) {
        Optional<UserFcmToken> existingOpt = tokenRepository.findByUserIdAndFcmToken(
                command.getUserId(), command.getFcmToken()
        );

        if (existingOpt.isPresent()) {
            UserFcmToken existing = existingOpt.get();
            existing.setDeviceType(command.getDeviceType() != null ? command.getDeviceType() : existing.getDeviceType());
            log.info("Updated existing FCM token for user {}", command.getUserId());
            return tokenRepository.save(existing);
        }

        UserFcmToken newToken = UserFcmToken.builder()
                .userId(command.getUserId())
                .fcmToken(command.getFcmToken())
                .deviceType(command.getDeviceType() != null ? command.getDeviceType() : "WEB")
                .build();

        UserFcmToken saved = tokenRepository.save(newToken);
        log.info("Registered new FCM token for user {}", command.getUserId());
        return saved;
    }
}
