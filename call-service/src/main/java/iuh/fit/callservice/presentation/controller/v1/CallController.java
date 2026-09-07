package iuh.fit.callservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.application.features.call.commands.end_call.EndCallCommand;
import iuh.fit.callservice.application.features.call.commands.end_call.EndCallCommandHandler;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallCommand;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallCommandHandler;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallResult;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallCommand;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallCommandHandler;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallResult;
import iuh.fit.callservice.application.features.call.commands.leave_call.LeaveCallCommand;
import iuh.fit.callservice.application.features.call.commands.leave_call.LeaveCallCommandHandler;
import iuh.fit.callservice.application.features.call.commands.toggle_media.ToggleMediaCommand;
import iuh.fit.callservice.application.features.call.commands.toggle_media.ToggleMediaCommandHandler;
import iuh.fit.callservice.application.features.call.queries.get_active_call.GetActiveCallQuery;
import iuh.fit.callservice.application.features.call.queries.get_active_call.GetActiveCallQueryHandler;
import iuh.fit.callservice.application.features.call.queries.get_active_call.GetActiveCallResult;
import iuh.fit.callservice.application.features.call.queries.get_call_history.GetCallHistoryQuery;
import iuh.fit.callservice.application.features.call.queries.get_call_history.GetCallHistoryQueryHandler;
import iuh.fit.callservice.application.features.call.queries.get_call_history.GetCallHistoryResult;
import iuh.fit.callservice.presentation.constants.ApiConstants;
import iuh.fit.callservice.presentation.dto.request.InitiateCallRequest;
import iuh.fit.callservice.presentation.dto.request.ToggleMediaRequest;
import iuh.fit.callservice.presentation.dto.response.CallHistoryResponse;
import iuh.fit.callservice.presentation.dto.response.CallSessionResponse;
import iuh.fit.callservice.presentation.mapper.CallPresentationMapper;
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
@RequestMapping(ApiConstants.CALL_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Audio/Video Call Management", description = "APIs for initiating 1-on-1 and Group Audio/Video Calls, joining, leaving, toggling media, and viewing call history")
@SecurityRequirement(name = "bearerAuth")
public class CallController {

    InitiateCallCommandHandler initiateCallCommandHandler;
    JoinCallCommandHandler joinCallCommandHandler;
    LeaveCallCommandHandler leaveCallCommandHandler;
    EndCallCommandHandler endCallCommandHandler;
    ToggleMediaCommandHandler toggleMediaCommandHandler;
    GetActiveCallQueryHandler getActiveCallQueryHandler;
    GetCallHistoryQueryHandler getCallHistoryQueryHandler;
    CallPresentationMapper callPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate audio/video call (1-on-1 or Group)", description = "Initiates a new audio or video call session with target users")
    public ResponseEntity<ApiResponse<CallSessionResponse>> initiateCall(@Valid @RequestBody InitiateCallRequest request) {
        UUID currentUserId = getCurrentUserId();
        InitiateCallCommand command = callPresentationMapper.toInitiateCommand(request, currentUserId);
        InitiateCallResult result = initiateCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(result), "Call initiated successfully"));
    }

    @PostMapping("/{callSessionId}/join")
    @Operation(summary = "Join ongoing call session", description = "Joins an active 1-on-1 or group call session")
    public ResponseEntity<ApiResponse<CallSessionResponse>> joinCall(@PathVariable UUID callSessionId) {
        UUID currentUserId = getCurrentUserId();
        JoinCallCommand command = callPresentationMapper.toJoinCommand(callSessionId, currentUserId);
        JoinCallResult result = joinCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(result), "Joined call session successfully"));
    }

    @PostMapping("/{callSessionId}/leave")
    @Operation(summary = "Leave call session", description = "Leaves an active call session")
    public ResponseEntity<ApiResponse<Void>> leaveCall(@PathVariable UUID callSessionId) {
        UUID currentUserId = getCurrentUserId();
        LeaveCallCommand command = callPresentationMapper.toLeaveCommand(callSessionId, currentUserId);
        leaveCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Left call session successfully"));
    }

    @PostMapping("/{callSessionId}/end")
    @Operation(summary = "End call session (Host only)", description = "Ends an active call session for all participants (Host only)")
    public ResponseEntity<ApiResponse<Void>> endCall(@PathVariable UUID callSessionId) {
        UUID currentUserId = getCurrentUserId();
        EndCallCommand command = callPresentationMapper.toEndCommand(callSessionId, currentUserId);
        endCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Call session ended successfully"));
    }

    @PutMapping("/{callSessionId}/media")
    @Operation(summary = "Toggle audio/video muted status", description = "Updates microphone or camera status (Mute/Unmute)")
    public ResponseEntity<ApiResponse<Void>> toggleMedia(
            @PathVariable UUID callSessionId,
            @Valid @RequestBody ToggleMediaRequest request) {
        UUID currentUserId = getCurrentUserId();
        ToggleMediaCommand command = callPresentationMapper.toToggleMediaCommand(request, callSessionId, currentUserId);
        toggleMediaCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Media status updated successfully"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active call session", description = "Retrieves current active call session of the authenticated user")
    public ResponseEntity<ApiResponse<CallSessionResponse>> getActiveCall() {
        UUID currentUserId = getCurrentUserId();
        GetActiveCallQuery query = GetActiveCallQuery.builder().currentUserId(currentUserId).build();
        GetActiveCallResult result = getActiveCallQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(result), "Active call session retrieved successfully"));
    }

    @GetMapping("/history")
    @Operation(summary = "Get call history", description = "Retrieves paginated call history for the authenticated user using BaseFilter")
    public ResponseEntity<ApiResponse<List<CallHistoryResponse>>> getCallHistory(@ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        UUID currentUserId = getCurrentUserId();
        GetCallHistoryQuery query = GetCallHistoryQuery.builder().currentUserId(currentUserId).filter(filter).build();
        PagedResponse<GetCallHistoryResult> result = getCallHistoryQueryHandler.handle(query);
        PagedResponse<CallHistoryResponse> pagedResponse = callPresentationMapper.toPagedHistoryResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Call history retrieved successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(CallServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
