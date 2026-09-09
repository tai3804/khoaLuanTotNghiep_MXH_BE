package iuh.fit.callservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallCommand;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallResult;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallCommand;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallResult;
import iuh.fit.callservice.application.features.call.commands.leave_call.LeaveCallCommand;
import iuh.fit.callservice.application.features.call.commands.end_call.EndCallCommand;
import iuh.fit.callservice.application.features.call.commands.toggle_media.ToggleMediaCommand;
import iuh.fit.callservice.application.features.call.queries.get_active_call.GetActiveCallResult;
import iuh.fit.callservice.application.features.call.queries.get_call_history.GetCallHistoryResult;
import iuh.fit.callservice.presentation.dto.request.InitiateCallRequest;
import iuh.fit.callservice.presentation.dto.request.ToggleMediaRequest;
import iuh.fit.callservice.presentation.dto.response.CallHistoryResponse;
import iuh.fit.callservice.presentation.dto.response.CallSessionResponse;
import iuh.fit.callservice.presentation.dto.request.WebRtcSignalRequest;
import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CallPresentationMapper {

    InitiateCallCommand toInitiateCommand(InitiateCallRequest request, UUID currentUserId);

    JoinCallCommand toJoinCommand(UUID callSessionId, UUID currentUserId);

    LeaveCallCommand toLeaveCommand(UUID callSessionId, UUID currentUserId);

    EndCallCommand toEndCommand(UUID callSessionId, UUID currentUserId);

    ToggleMediaCommand toToggleMediaCommand(ToggleMediaRequest request, UUID callSessionId, UUID currentUserId);

    CallSessionResponse toResponse(InitiateCallResult result);

    CallSessionResponse toResponse(JoinCallResult result);

    CallSessionResponse toResponse(GetActiveCallResult result);

    CallHistoryResponse toResponse(GetCallHistoryResult result);

    List<CallHistoryResponse> toHistoryResponseList(List<GetCallHistoryResult> results);

    @Mapping(target = "callSessionId", source = "callSessionId")
    @Mapping(target = "senderId", source = "senderId")
    @Mapping(target = "targetUserId", source = "targetUserId")
    @Mapping(target = "signalType", source = "request.signalType")
    @Mapping(target = "sdp", source = "request.sdp")
    @Mapping(target = "candidate", source = "request.candidate")
    @Mapping(target = "audioMuted", source = "request.audioMuted")
    @Mapping(target = "videoMuted", source = "request.videoMuted")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    WebRtcSignalResponse toSignalResponse(WebRtcSignalRequest request, UUID callSessionId, UUID senderId, UUID targetUserId);

    default PagedResponse<CallHistoryResponse> toPagedHistoryResponse(PagedResponse<GetCallHistoryResult> pagedResult) {
        if (pagedResult == null) return null;
        List<CallHistoryResponse> content = pagedResult.getContent() == null ? java.util.Collections.emptyList() :
                pagedResult.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<CallHistoryResponse>builder()
                .content(content)
                .page(pagedResult.getPage())
                .size(pagedResult.getSize())
                .totalElements(pagedResult.getTotalElements())
                .totalPages(pagedResult.getTotalPages())
                .last(pagedResult.isLast())
                .build();
    }
}
