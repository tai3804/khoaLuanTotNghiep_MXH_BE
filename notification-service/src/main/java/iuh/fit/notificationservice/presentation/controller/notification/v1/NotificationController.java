package iuh.fit.notificationservice.presentation.controller.notification.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.notificationservice.application.features.notification.commands.create_notification.CreateNotificationCommand;
import iuh.fit.notificationservice.application.features.notification.commands.create_notification.CreateNotificationHandler;
import iuh.fit.notificationservice.application.features.notification.commands.mark_all_read.MarkAllNotificationsAsReadCommand;
import iuh.fit.notificationservice.application.features.notification.commands.mark_all_read.MarkAllNotificationsAsReadHandler;
import iuh.fit.notificationservice.application.features.notification.commands.mark_read.MarkNotificationAsReadCommand;
import iuh.fit.notificationservice.application.features.notification.commands.mark_read.MarkNotificationAsReadHandler;
import iuh.fit.notificationservice.application.features.notification.commands.send_email.SendEmailHandler;
import iuh.fit.notificationservice.application.features.notification.queries.get_notifications.GetNotificationsHandler;
import iuh.fit.notificationservice.application.features.notification.queries.get_notifications.GetNotificationsQuery;
import iuh.fit.notificationservice.application.features.notification.queries.get_notifications.NotificationResult;
import iuh.fit.notificationservice.application.features.notification.queries.get_unread_count.GetUnreadNotificationCountHandler;
import iuh.fit.notificationservice.application.features.notification.queries.get_unread_count.GetUnreadNotificationCountQuery;
import iuh.fit.notificationservice.domain.entities.Notification;
import iuh.fit.notificationservice.presentation.constants.ApiConstants;
import iuh.fit.notificationservice.presentation.dto.request.CreateNotificationRequest;
import iuh.fit.notificationservice.presentation.dto.request.SendEmailRequest;
import iuh.fit.notificationservice.presentation.dto.response.NotificationResponse;
import iuh.fit.notificationservice.presentation.dto.response.UnreadCountResponse;
import iuh.fit.notificationservice.presentation.mapper.NotificationPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.NOTIFICATION_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "In-App Notification Center", description = "APIs for in-app notification history, unread badge count, and reading status")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    SendEmailHandler sendEmailHandler;
    CreateNotificationHandler createNotificationHandler;
    MarkNotificationAsReadHandler markNotificationAsReadHandler;
    MarkAllNotificationsAsReadHandler markAllNotificationsAsReadHandler;
    GetNotificationsHandler getNotificationsHandler;
    GetUnreadNotificationCountHandler getUnreadNotificationCountHandler;
    NotificationPresentationMapper mapper;
    JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves paginated list of in-app notifications for the authenticated user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        UUID currentUserId = getCurrentUserId();
        GetNotificationsQuery query = GetNotificationsQuery.builder()
                .recipientId(currentUserId)
                .filter(filter)
                .build();

        PagedResponse<NotificationResult> result = getNotificationsHandler.handle(query);
        PagedResponse<NotificationResponse> pagedResponse = mapper.toPagedResponse(result);

        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Notifications retrieved successfully"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count badge", description = "Returns the count of unread notifications for badge icon display")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount() {
        UUID currentUserId = getCurrentUserId();
        GetUnreadNotificationCountQuery query = GetUnreadNotificationCountQuery.builder()
                .recipientId(currentUserId)
                .build();

        long count = getUnreadNotificationCountHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.success(
                UnreadCountResponse.builder().unreadCount(count).build(),
                "Unread count retrieved successfully"
        ));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Marks a specific notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        UUID currentUserId = getCurrentUserId();
        MarkNotificationAsReadCommand command = MarkNotificationAsReadCommand.builder()
                .notificationId(id)
                .recipientId(currentUserId)
                .build();

        Notification notification = markNotificationAsReadHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(notification), "Notification marked as read"));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks all notifications of current user as read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead() {
        UUID currentUserId = getCurrentUserId();
        MarkAllNotificationsAsReadCommand command = MarkAllNotificationsAsReadCommand.builder()
                .recipientId(currentUserId)
                .build();

        int count = markAllNotificationsAsReadHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(count, "All notifications marked as read"));
    }

    @PostMapping
    @Operation(summary = "Create in-app notification", description = "Creates a new in-app notification and triggers FCM push notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {
        UUID actorId = tryGetCurrentUserId();
        CreateNotificationCommand command = mapper.toCreateCommand(request, actorId);
        Notification notification = createNotificationHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(notification), "Notification created successfully"));
    }

    @PostMapping("/send-email")
    @Operation(summary = "Send email notification", description = "Sends email via SMTP/Brevo API")
    public ResponseEntity<ApiResponse<Void>> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        sendEmailHandler.handle(mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Email sent successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }

    private UUID tryGetCurrentUserId() {
        try {
            String userIdStr = jwtUtil.getCurrentUserId();
            return userIdStr != null ? UUID.fromString(userIdStr) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
