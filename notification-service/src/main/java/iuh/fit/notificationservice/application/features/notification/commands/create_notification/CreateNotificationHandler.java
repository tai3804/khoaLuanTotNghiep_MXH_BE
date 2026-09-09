package iuh.fit.notificationservice.application.features.notification.commands.create_notification;

import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import iuh.fit.notificationservice.infrastructure.firebase.FcmPushNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateNotificationHandler {

    NotificationRepository notificationRepository;
    FcmPushNotificationService fcmPushNotificationService;

    @Transactional
    public Notification handle(CreateNotificationCommand command) {
        Notification notification = Notification.builder()
                .recipientId(command.getRecipientId())
                .actorId(command.getActorId())
                .type(command.getType())
                .title(command.getTitle())
                .content(command.getContent())
                .targetId(command.getTargetId())
                .targetUrl(command.getTargetUrl())
                .avatarUrl(command.getAvatarUrl())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Saved in-app notification [{}] for recipient {}", saved.getType(), saved.getRecipientId());

        try {
            Map<String, String> data = new HashMap<>();
            if (saved.getTargetId() != null) data.put("targetId", saved.getTargetId());
            if (saved.getTargetUrl() != null) data.put("targetUrl", saved.getTargetUrl());
            data.put("notificationId", saved.getId().toString());
            data.put("type", saved.getType().name());

            fcmPushNotificationService.sendPushNotification(
                    saved.getRecipientId(),
                    saved.getTitle(),
                    saved.getContent(),
                    data
            );
        } catch (Exception e) {
            log.warn("Failed to trigger FCM push notification for notification {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }
}
