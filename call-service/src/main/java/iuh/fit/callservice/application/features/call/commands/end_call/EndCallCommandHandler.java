package iuh.fit.callservice.application.features.call.commands.end_call;

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
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EndCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;

    @Transactional
    public void handle(EndCallCommand command) {
        CallSession session = callSessionRepository.findById(command.getCallSessionId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        if (!session.getHostUserId().equals(command.getCurrentUserId())) {
            throw new BusinessException(CallServiceErrorCode.UNAUTHORIZED_CALL_HOST);
        }

        session.setStatus(CallStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        if (session.getStartedAt() != null) {
            session.setDurationInSeconds(Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds());
        }
        callSessionRepository.save(session);

        List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
        for (CallParticipant p : participants) {
            if (p.getStatus() == ParticipantStatus.CONNECTED || p.getStatus() == ParticipantStatus.RINGING || p.getStatus() == ParticipantStatus.INVITED) {
                p.setStatus(ParticipantStatus.LEFT);
                p.setLeftAt(LocalDateTime.now());
            }
        }
        callParticipantRepository.saveAll(participants);
    }
}
