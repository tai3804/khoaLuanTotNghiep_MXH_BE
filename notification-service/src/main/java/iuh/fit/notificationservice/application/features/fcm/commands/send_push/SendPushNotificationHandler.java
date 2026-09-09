package iuh.fit.notificationservice.application.features.fcm.commands.send_push;

import iuh.fit.notificationservice.infrastructure.firebase.FcmPushNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendPushNotificationHandler {

    FcmPushNotificationService pushNotificationService;

    public int handle(SendPushNotificationCommand command) {
        log.info("Handling SendPushNotificationCommand for user {}: title='{}'", command.getTargetUserId(), command.getTitle());
        return pushNotificationService.sendPushNotification(
                command.getTargetUserId(),
                command.getTitle(),
                command.getBody(),
                command.getData()
        );
    }
}
