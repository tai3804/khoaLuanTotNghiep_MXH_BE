package iuh.fit.notificationservice.application.features.notification.commands.create_notification;

import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import iuh.fit.notificationservice.domain.repositories.NotificationSettingRepository;
import iuh.fit.notificationservice.infrastructure.firebase.FcmPushNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    NotificationSettingRepository notificationSettingRepository;
    FcmPushNotificationService fcmPushNotificationService;
    SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Notification handle(CreateNotificationCommand command) {
        // UC-NO05: Check recipient's notification preference
        boolean isEnabled = notificationSettingRepository.findByUserId(command.getRecipientId())
                .map(setting -> switch (command.getType()) {
                    case LIKE_POST -> setting.isLikePost();
                    case COMMENT_POST, REPLY_COMMENT, TAG_COMMENT -> setting.isCommentPost();
                    case SHARE_POST -> setting.isSharePost();
                    case FRIEND_REQUEST, ACCEPT_FRIEND, FOLLOW_USER -> setting.isFriendRequest();
                    case NEW_MESSAGE -> setting.isMessage();
                    case CALL_INCOMING, CALL_REJECTED, CALL_MISSED -> setting.isCall();
                    case SYSTEM -> setting.isSystem();
                    default -> true;
                })
                .orElse(true);

        if (!isEnabled) {
            log.info("Notification [{}] skipped because recipient {} disabled this category in settings",
                    command.getType(), command.getRecipientId());
            return null;
        }

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

        // UC-NO06: Realtime WebSocket push
        try {
            long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(saved.getRecipientId());
            messagingTemplate.convertAndSend("/topic/notifications." + saved.getRecipientId(), (Object) saved);
            messagingTemplate.convertAndSend("/topic/notifications.count." + saved.getRecipientId(), (Object) Map.of("unreadCount", unreadCount));

            log.info("Pushed realtime notification [{}] to WebSocket topic /topic/notifications.{}", saved.getId(), saved.getRecipientId());
        } catch (Exception e) {
            log.warn("Failed to push notification via WebSocket: {}", e.getMessage());
        }

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

