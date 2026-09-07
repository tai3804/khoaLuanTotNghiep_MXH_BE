package iuh.fit.callservice.application.features.call.queries.get_active_call;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
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
        CallSession session = callSessionRepository.findActiveCallSessionByUserId(query.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
        List<GetActiveCallResult.ParticipantResult> participantResults = participants.stream()
                .map(callFeatureMapper::toActiveParticipantResult)
                .toList();

        return callFeatureMapper.toGetActiveCallResult(session, participantResults);
    }
}
