package iuh.fit.notificationservice.application.features.notification.commands.mark_all_read;

import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarkAllNotificationsAsReadHandler {

    NotificationRepository notificationRepository;
    SimpMessagingTemplate messagingTemplate;

    @Transactional
    public int handle(MarkAllNotificationsAsReadCommand command) {
        int count = notificationRepository.markAllAsReadByRecipientId(command.getRecipientId(), Instant.now());
        log.info("Marked all notifications ({}) as read for user {}", count, command.getRecipientId());

        try {
            messagingTemplate.convertAndSend("/topic/notifications.count." + command.getRecipientId(), (Object) Map.of("unreadCount", 0L));
        } catch (Exception e) {

            log.warn("Failed to push unread count via WebSocket: {}", e.getMessage());
        }

        return count;
    }
}

