package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.chatservice.application.service.UserPresenceService;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.response.UserPresenceResponse;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/presence")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Presence Management", description = "APIs for querying user online/offline status and last active time [UC-CH06]")
@SecurityRequirement(name = "bearerAuth")
public class UserPresenceController {

    UserPresenceService userPresenceService;

    @Operation(summary = "Get user presence status", description = "Query if a user is online and their last active timestamp")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserPresenceResponse>> getPresence(@PathVariable UUID userId) {
        UserPresenceResponse presence = userPresenceService.getPresence(userId);
        return ResponseEntity.ok(ApiResponse.success(presence));
    }

    @Operation(summary = "Batch get user presence status", description = "Query online/offline status for multiple users at once")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<UUID, UserPresenceResponse>>> getBatchPresence(@RequestBody List<UUID> userIds) {
        Map<UUID, UserPresenceResponse> presences = userPresenceService.getBatchPresence(userIds);
        return ResponseEntity.ok(ApiResponse.success(presences));
    }
}
