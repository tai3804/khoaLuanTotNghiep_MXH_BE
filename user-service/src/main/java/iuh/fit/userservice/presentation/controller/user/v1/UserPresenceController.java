package iuh.fit.userservice.presentation.controller.user.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.features.user_presence.commands.update_presence.UpdatePresenceCommand;
import iuh.fit.userservice.application.features.user_presence.commands.update_presence.UpdatePresenceCommandHandler;
import iuh.fit.userservice.application.features.user_presence.queries.get_user_presence.GetUserPresenceQuery;
import iuh.fit.userservice.application.features.user_presence.queries.get_user_presence.GetUserPresenceQueryHandler;
import iuh.fit.userservice.application.features.user_presence.queries.get_user_presence.UserPresenceResult;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.dto.request.BatchPresenceRequest;
import iuh.fit.userservice.presentation.dto.response.UserPresenceResponse;
import iuh.fit.userservice.presentation.mapper.UserPresencePresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.USER_API + "/presence")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Presence & Activity Status", description = "APIs for tracking user online/offline status and heartbeat signals")
@SecurityRequirement(name = "bearerAuth")
public class UserPresenceController {

    UpdatePresenceCommandHandler updatePresenceCommandHandler;
    GetUserPresenceQueryHandler getUserPresenceQueryHandler;
    UserPresencePresentationMapper userPresencePresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/heartbeat")
    @Operation(summary = "Send presence heartbeat", description = "Periodic heartbeat sent by frontend to maintain current user online status")
    public ResponseEntity<ApiResponse<Void>> sendHeartbeat() {
        UUID currentUserId = getCurrentUserId();
        UpdatePresenceCommand command = UpdatePresenceCommand.builder()
                .userId(currentUserId)
                .isOnline(true)
                .build();
        updatePresenceCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Heartbeat received"));
    }

    @PostMapping("/offline")
    @Operation(summary = "Set offline status", description = "Sets current user status to offline explicitly (e.g. on logout)")
    public ResponseEntity<ApiResponse<Void>> setOffline() {
        UUID currentUserId = getCurrentUserId();
        UpdatePresenceCommand command = UpdatePresenceCommand.builder()
                .userId(currentUserId)
                .isOnline(false)
                .build();
        updatePresenceCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Offline status set"));
    }

    @GetMapping("/{targetUserId}")
    @Operation(summary = "Get user presence status", description = "Retrieves online/offline status and last active timestamp of a target user")
    public ResponseEntity<ApiResponse<UserPresenceResponse>> getUserPresence(@PathVariable UUID targetUserId) {
        GetUserPresenceQuery query = GetUserPresenceQuery.builder()
                .targetUserId(targetUserId)
                .build();
        List<UserPresenceResult> results = getUserPresenceQueryHandler.handle(query);
        if (results.isEmpty()) {
            throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
        }
        UserPresenceResponse response = userPresencePresentationMapper.toResponse(results.get(0));
        return ResponseEntity.ok(ApiResponse.success(response, "User presence retrieved"));
    }

    @PostMapping("/batch")
    @Operation(summary = "Get batch user presence status", description = "Retrieves presence status for multiple target user IDs simultaneously")
    public ResponseEntity<ApiResponse<List<UserPresenceResponse>>> getBatchUserPresence(
            @Valid @RequestBody BatchPresenceRequest request) {
        GetUserPresenceQuery query = GetUserPresenceQuery.builder()
                .targetUserIds(request.getUserIds())
                .build();
        List<UserPresenceResult> results = getUserPresenceQueryHandler.handle(query);
        List<UserPresenceResponse> responseList = userPresencePresentationMapper.toResponseList(results);
        return ResponseEntity.ok(ApiResponse.success(responseList, "Batch presence retrieved"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
