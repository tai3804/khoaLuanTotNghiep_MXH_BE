package iuh.fit.callservice.application.features.call.commands.join_call;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.application.mapper.CallFeatureMapper;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.*;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JoinCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;

    @Transactional
    public JoinCallResult handle(JoinCallCommand command) {
        CallSession session = callSessionRepository.findById(command.getCallSessionId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        if (session.getStatus() == CallStatus.ENDED) {
            throw new BusinessException(CallServiceErrorCode.CALL_SESSION_ALREADY_ENDED);
        }

        Optional<CallParticipant> participantOpt = callParticipantRepository
                .findByCallSessionIdAndUserId(command.getCallSessionId(), command.getCurrentUserId());

        CallParticipant participant;
        if (participantOpt.isPresent()) {
            participant = participantOpt.get();
            participant.setStatus(ParticipantStatus.CONNECTED);
            participant.setJoinedAt(LocalDateTime.now());
        } else {
            participant = CallParticipant.builder()
                    .callSessionId(command.getCallSessionId())
                    .userId(command.getCurrentUserId())
                    .role(ParticipantRole.PARTICIPANT)
                    .status(ParticipantStatus.CONNECTED)
                    .joinedAt(LocalDateTime.now())
                    .build();
        }
        callParticipantRepository.save(participant);

        if (session.getStatus() == CallStatus.INITIATED) {
            session.setStatus(CallStatus.ACTIVE);
            callSessionRepository.save(session);
        }

        List<CallParticipant> allParticipants = callParticipantRepository.findByCallSessionId(session.getId());
        List<JoinCallResult.ParticipantResult> participantResults = allParticipants.stream()
                .map(callFeatureMapper::toJoinParticipantResult)
                .toList();

        return callFeatureMapper.toJoinCallResult(session, participantResults);
    }
}
