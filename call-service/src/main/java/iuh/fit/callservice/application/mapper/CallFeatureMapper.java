package iuh.fit.callservice.application.mapper;

import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.application.features.call.commands.initiate_call.InitiateCallResult;
import iuh.fit.callservice.application.features.call.commands.join_call.JoinCallResult;
import iuh.fit.callservice.application.features.call.queries.get_active_call.GetActiveCallResult;
import iuh.fit.callservice.application.features.call.queries.get_call_history.GetCallHistoryResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CallFeatureMapper {

    @Mapping(target = "callSessionId", source = "session.id")
    InitiateCallResult toInitiateCallResult(CallSession session, List<InitiateCallResult.ParticipantResult> participants);

    InitiateCallResult.ParticipantResult toInitiateParticipantResult(CallParticipant participant);

    @Mapping(target = "callSessionId", source = "session.id")
    JoinCallResult toJoinCallResult(CallSession session, List<JoinCallResult.ParticipantResult> participants);

    JoinCallResult.ParticipantResult toJoinParticipantResult(CallParticipant participant);

    @Mapping(target = "callSessionId", source = "session.id")
    GetActiveCallResult toGetActiveCallResult(CallSession session, List<GetActiveCallResult.ParticipantResult> participants);

    GetActiveCallResult.ParticipantResult toActiveParticipantResult(CallParticipant participant);

    @Mapping(target = "callSessionId", source = "session.id")
    @Mapping(target = "status", source = "session.status")
    @Mapping(target = "myRole", source = "myParticipant.role")
    @Mapping(target = "myStatus", source = "myParticipant.status")
    GetCallHistoryResult toGetCallHistoryResult(CallSession session, CallParticipant myParticipant);
}

