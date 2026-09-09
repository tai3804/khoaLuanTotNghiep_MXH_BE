package iuh.fit.notificationservice.application.features.notification.queries.get_unread_count;

import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUnreadNotificationCountHandler {

    NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public long handle(GetUnreadNotificationCountQuery query) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(query.getRecipientId());
    }
}
