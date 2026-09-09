package iuh.fit.notificationservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.notificationservice.application.features.notification.queries.get_notifications.NotificationResult;
import iuh.fit.notificationservice.domain.entities.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationFeatureMapper {

    NotificationResult toResult(Notification entity);

    List<NotificationResult> toResultList(List<Notification> entities);

    default PagedResponse<NotificationResult> toPagedResult(Page<Notification> page) {
        if (page == null) return null;
        List<NotificationResult> content = page.getContent() == null ? Collections.emptyList() :
                page.getContent().stream().map(this::toResult).toList();
        return PagedResponse.<NotificationResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
