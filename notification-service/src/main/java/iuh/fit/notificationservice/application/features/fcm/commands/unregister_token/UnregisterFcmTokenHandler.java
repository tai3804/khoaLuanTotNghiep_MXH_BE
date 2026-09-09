package iuh.fit.notificationservice.application.features.fcm.commands.unregister_token;

import iuh.fit.notificationservice.domain.repositories.UserFcmTokenRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnregisterFcmTokenHandler {

    UserFcmTokenRepository tokenRepository;

    @Transactional
    public void handle(UnregisterFcmTokenCommand command) {
        if (command.getUserId() != null) {
            tokenRepository.deleteByUserIdAndFcmToken(command.getUserId(), command.getFcmToken());
            log.info("Deleted FCM token for user {}", command.getUserId());
        } else {
            tokenRepository.deleteByFcmToken(command.getFcmToken());
            log.info("Deleted FCM token by token string");
        }
    }
}
