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
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
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
    iuh.fit.callservice.application.features.call.commands.reject_call.RejectCallCommandHandler rejectCallCommandHandler;
    EndCallCommandHandler endCallCommandHandler;
    ToggleMediaCommandHandler toggleMediaCommandHandler;
    GetActiveCallQueryHandler getActiveCallQueryHandler;
    GetCallHistoryQueryHandler getCallHistoryQueryHandler;
    CallPresentationMapper callPresentationMapper;
    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
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

    @PostMapping("/{callSessionId}/reject")
    @Operation(summary = "Reject incoming call session", description = "Declines an incoming call invitation")
    public ResponseEntity<ApiResponse<Void>> rejectCall(@PathVariable UUID callSessionId) {
        UUID currentUserId = getCurrentUserId();
        iuh.fit.callservice.application.features.call.commands.reject_call.RejectCallCommand command =
                callPresentationMapper.toRejectCommand(callSessionId, currentUserId);
        rejectCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Rejected call session successfully"));
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

    @GetMapping("/{callSessionId}")
    @Operation(summary = "Get call session details", description = "Retrieves session details by call session ID")
    public ResponseEntity<ApiResponse<CallSessionResponse>> getCallSession(@PathVariable UUID callSessionId) {
        CallSession session = callSessionRepository.findById(callSessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "Call session not found"));
        }
        List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(session, participants), "Call session retrieved successfully"));
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get call session for post", description = "Retrieves active or latest call session associated with a post")
    public ResponseEntity<ApiResponse<CallSessionResponse>> getCallSessionByPost(@PathVariable UUID postId) {
        CallSession session = callSessionRepository.findFirstByConversationIdOrderByStartedAtDesc(postId).orElse(null);
        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No call session found for post"));
        }
        List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(session, participants), "Post call session retrieved successfully"));
    }

    @PostMapping("/post/{postId}/join")
    @Operation(summary = "Join call session for post", description = "Joins the active call session associated with a post")
    public ResponseEntity<ApiResponse<CallSessionResponse>> joinCallByPost(@PathVariable UUID postId) {
        UUID currentUserId = getCurrentUserId();
        CallSession session = callSessionRepository.findByConversationIdAndStatusIn(postId, List.of(CallStatus.INITIATED, CallStatus.ACTIVE))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        JoinCallCommand command = callPresentationMapper.toJoinCommand(session.getId(), currentUserId);
        JoinCallResult result = joinCallCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(callPresentationMapper.toResponse(result), "Joined post call session successfully"));
    }

    @PostMapping("/post/{postId}/end")
    @Operation(summary = "End call session for post", description = "Ends the active call session associated with a post (Host only)")
    public ResponseEntity<ApiResponse<Void>> endCallByPost(@PathVariable UUID postId) {
        UUID currentUserId = getCurrentUserId();
        List<CallSession> sessions = callSessionRepository.findByConversationIdAndStatusIn(postId, List.of(CallStatus.INITIATED, CallStatus.ACTIVE));
        for (CallSession session : sessions) {
            EndCallCommand command = callPresentationMapper.toEndCommand(session.getId(), currentUserId);
            endCallCommandHandler.handle(command);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Post call session ended successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(CallServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
