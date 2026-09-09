package iuh.fit.notificationservice.presentation.controller.notification.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.CommonErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.notificationservice.application.features.fcm.commands.register_token.RegisterFcmTokenCommand;
import iuh.fit.notificationservice.application.features.fcm.commands.register_token.RegisterFcmTokenHandler;
import iuh.fit.notificationservice.application.features.fcm.commands.send_push.SendPushNotificationCommand;
import iuh.fit.notificationservice.application.features.fcm.commands.send_push.SendPushNotificationHandler;
import iuh.fit.notificationservice.application.features.fcm.commands.unregister_token.UnregisterFcmTokenCommand;
import iuh.fit.notificationservice.application.features.fcm.commands.unregister_token.UnregisterFcmTokenHandler;
import iuh.fit.notificationservice.domain.entities.UserFcmToken;
import iuh.fit.notificationservice.presentation.constants.ApiConstants;
import iuh.fit.notificationservice.presentation.dto.request.RegisterFcmTokenRequest;
import iuh.fit.notificationservice.presentation.dto.request.SendPushNotificationRequest;
import iuh.fit.notificationservice.presentation.dto.request.UnregisterFcmTokenRequest;
import iuh.fit.notificationservice.presentation.dto.response.FcmTokenResponse;
import iuh.fit.notificationservice.presentation.mapper.NotificationPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.NOTIFICATION_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "FCM Push Notification", description = "APIs for FCM device token registration and Web/Mobile push notifications")
@SecurityRequirement(name = "bearerAuth")
public class FcmTokenController {

    RegisterFcmTokenHandler registerFcmTokenHandler;
    UnregisterFcmTokenHandler unregisterFcmTokenHandler;
    SendPushNotificationHandler sendPushNotificationHandler;
    NotificationPresentationMapper mapper;
    JwtUtil jwtUtil;

    @PostMapping("/fcm-tokens")
    @Operation(summary = "Register FCM device token", description = "Registers or updates an FCM token for background/offline push notifications on Mobile or Web Browser")
    public ResponseEntity<ApiResponse<FcmTokenResponse>> registerFcmToken(
            @Valid @RequestBody RegisterFcmTokenRequest request) {
        UUID currentUserId = getCurrentUserId();
        RegisterFcmTokenCommand command = mapper.toRegisterCommand(request, currentUserId);
        UserFcmToken result = registerFcmTokenHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(result), "FCM token registered successfully"));
    }

    @DeleteMapping("/fcm-tokens")
    @Operation(summary = "Unregister FCM device token", description = "Deletes an FCM token when a user logs out or disables push notifications")
    public ResponseEntity<ApiResponse<Void>> unregisterFcmToken(
            @Valid @RequestBody UnregisterFcmTokenRequest request) {
        UUID currentUserId = getCurrentUserId();
        UnregisterFcmTokenCommand command = mapper.toUnregisterCommand(request, currentUserId);
        unregisterFcmTokenHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "FCM token unregistered successfully"));
    }

    @PostMapping("/push/send")
    @Operation(summary = "Send FCM push notification", description = "Sends a real-time Web/Mobile FCM push notification to a target user")
    public ResponseEntity<ApiResponse<Integer>> sendPushNotification(
            @Valid @RequestBody SendPushNotificationRequest request) {
        SendPushNotificationCommand command = mapper.toSendPushCommand(request);
        int count = sendPushNotificationHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(count, "FCM push notification processed"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
