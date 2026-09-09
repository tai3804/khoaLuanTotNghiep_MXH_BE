package iuh.fit.notificationservice.application.features.notification.commands.mark_read;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.CommonErrorCode;
import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarkNotificationAsReadHandler {

    NotificationRepository notificationRepository;

    @Transactional
    public Notification handle(MarkNotificationAsReadCommand command) {
        Notification notification = notificationRepository.findByIdAndRecipientId(
                command.getNotificationId(), command.getRecipientId()
        ).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.info("Marked notification {} as read for user {}", notification.getId(), command.getRecipientId());
        }

        return notification;
    }
}
