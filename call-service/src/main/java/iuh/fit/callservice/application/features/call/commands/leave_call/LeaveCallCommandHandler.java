package iuh.fit.callservice.application.features.call.commands.leave_call;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaveCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;

    @Transactional
    public void handle(LeaveCallCommand command) {
        CallSession session = callSessionRepository.findById(command.getCallSessionId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        CallParticipant participant = callParticipantRepository
                .findByCallSessionIdAndUserId(command.getCallSessionId(), command.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.NOT_A_CALL_PARTICIPANT));

        participant.setStatus(ParticipantStatus.LEFT);
        participant.setLeftAt(LocalDateTime.now());
        callParticipantRepository.save(participant);

        long connectedCount = callParticipantRepository.countByCallSessionIdAndStatus(session.getId(), ParticipantStatus.CONNECTED);
        if (connectedCount == 0 && session.getStatus() != CallStatus.ENDED) {
            session.setStatus(CallStatus.ENDED);
            session.setEndedAt(LocalDateTime.now());
            if (session.getStartedAt() != null) {
                session.setDurationInSeconds(Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds());
            }
            callSessionRepository.save(session);
        }
    }
}
