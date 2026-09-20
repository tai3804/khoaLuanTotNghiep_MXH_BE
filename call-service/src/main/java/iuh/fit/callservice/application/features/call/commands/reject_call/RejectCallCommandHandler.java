package iuh.fit.callservice.application.features.call.commands.reject_call;

import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RejectCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void handle(RejectCallCommand command) {
        CallSession session = callSessionRepository.findById(command.getCallSessionId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.CALL_SESSION_NOT_FOUND));

        CallParticipant participant = callParticipantRepository
                .findByCallSessionIdAndUserId(command.getCallSessionId(), command.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(CallServiceErrorCode.NOT_A_CALL_PARTICIPANT));

        participant.setStatus(ParticipantStatus.DECLINED);
        participant.setLeftAt(LocalDateTime.now());
        callParticipantRepository.save(participant);

        if (session.getStatus() == CallStatus.INITIATED) {
            session.setStatus(CallStatus.REJECTED);
            session.setEndedAt(LocalDateTime.now());
            session.setDurationInSeconds(0);
            callSessionRepository.save(session);
        }

        // Notify caller (host) that call was rejected
        WebRtcSignalResponse rejectSignal = WebRtcSignalResponse.builder()
                .callSessionId(session.getId())
                .senderId(command.getCurrentUserId())
                .targetUserId(session.getHostUserId())
                .signalType(WebRtcSignalType.REJECT)
                .mediaType(session.getMediaType())
                .timestamp(Instant.now())
                .build();

        try {
            messagingTemplate.convertAndSendToUser(
                    session.getHostUserId().toString(),
                    "/queue/call-signal",
                    rejectSignal
            );
            messagingTemplate.convertAndSend(
                    "/topic/call-user." + session.getHostUserId(),
                    rejectSignal
            );
            messagingTemplate.convertAndSend(
                    "/topic/call/" + session.getId(),
                    rejectSignal
            );
            log.info("Sent REJECT signal for call session {} from participant {}", session.getId(), command.getCurrentUserId());
        } catch (Exception e) {
            log.error("Failed to broadcast REJECT signal: {}", e.getMessage());
        }
    }
}
