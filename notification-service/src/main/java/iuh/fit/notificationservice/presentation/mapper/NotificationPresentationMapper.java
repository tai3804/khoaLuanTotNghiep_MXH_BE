package iuh.fit.notificationservice.presentation.mapper;

import iuh.fit.notificationservice.application.features.fcm.commands.register_token.RegisterFcmTokenCommand;
import iuh.fit.notificationservice.application.features.fcm.commands.send_push.SendPushNotificationCommand;
import iuh.fit.notificationservice.application.features.fcm.commands.unregister_token.UnregisterFcmTokenCommand;
import iuh.fit.notificationservice.application.features.notification.commands.send_email.SendEmailCommand;
import iuh.fit.notificationservice.domain.entities.UserFcmToken;
import iuh.fit.notificationservice.presentation.dto.request.RegisterFcmTokenRequest;
import iuh.fit.notificationservice.presentation.dto.request.SendEmailRequest;
import iuh.fit.notificationservice.presentation.dto.request.SendPushNotificationRequest;
import iuh.fit.notificationservice.presentation.dto.request.UnregisterFcmTokenRequest;
import iuh.fit.notificationservice.presentation.dto.response.FcmTokenResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.notificationservice.application.features.notification.commands.create_notification.CreateNotificationCommand;
import iuh.fit.notificationservice.application.features.notification.queries.get_notifications.NotificationResult;
import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.presentation.dto.request.CreateNotificationRequest;
import iuh.fit.notificationservice.presentation.dto.response.NotificationResponse;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationPresentationMapper {

    SendEmailCommand toCommand(SendEmailRequest request);

    @Mapping(target = "userId", source = "userId")
    RegisterFcmTokenCommand toRegisterCommand(RegisterFcmTokenRequest request, UUID userId);

    @Mapping(target = "userId", source = "userId")
    UnregisterFcmTokenCommand toUnregisterCommand(UnregisterFcmTokenRequest request, UUID userId);

    SendPushNotificationCommand toSendPushCommand(SendPushNotificationRequest request);

    FcmTokenResponse toResponse(UserFcmToken entity);

    @Mapping(target = "actorId", source = "actorId")
    CreateNotificationCommand toCreateCommand(CreateNotificationRequest request, UUID actorId);

    NotificationResponse toResponse(Notification entity);

    NotificationResponse toResponse(NotificationResult result);

    default PagedResponse<NotificationResponse> toPagedResponse(PagedResponse<NotificationResult> pagedResult) {
        if (pagedResult == null) return null;
        List<NotificationResponse> content = pagedResult.getContent() == null ? Collections.emptyList() :
                pagedResult.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<NotificationResponse>builder()
                .content(content)
                .page(pagedResult.getPage())
                .size(pagedResult.getSize())
                .totalElements(pagedResult.getTotalElements())
                .totalPages(pagedResult.getTotalPages())
                .last(pagedResult.isLast())
                .build();
    }
}


