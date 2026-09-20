package iuh.fit.callservice.application.features.call.queries.get_active_call;

import iuh.fit.callservice.application.mapper.CallFeatureMapper;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetActiveCallQueryHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;

    @Transactional(readOnly = true)
    public GetActiveCallResult handle(GetActiveCallQuery query) {
        CallSession session = callSessionRepository.findActiveCallSessionsByUserId(query.getCurrentUserId()).stream()
                .findFirst()
                .orElse(null);
        // No active call is normal during application bootstrap. Returning
        // null lets the client recover incoming calls without creating a 404.
        if (session == null) {
            return null;
        }

        List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
        List<GetActiveCallResult.ParticipantResult> participantResults = participants.stream()
                .map(callFeatureMapper::toActiveParticipantResult)
                .toList();

        return callFeatureMapper.toGetActiveCallResult(session, participantResults);
    }
}
