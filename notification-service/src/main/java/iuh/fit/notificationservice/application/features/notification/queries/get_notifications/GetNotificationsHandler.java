package iuh.fit.notificationservice.application.features.notification.queries.get_notifications;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.notificationservice.application.mapper.NotificationFeatureMapper;
import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.domain.repositories.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetNotificationsHandler {

    NotificationRepository notificationRepository;
    NotificationFeatureMapper featureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResult> handle(GetNotificationsQuery query) {
        int page = query.getFilter() != null ? query.getFilter().getPage() : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                query.getRecipientId(), pageable
        );

        return featureMapper.toPagedResult(notificationPage);
    }
}
