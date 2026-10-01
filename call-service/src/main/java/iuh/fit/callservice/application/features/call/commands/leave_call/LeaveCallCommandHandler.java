package iuh.fit.callservice.application.features.call.commands.leave_call;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.callservice.application.exception.CallServiceErrorCode;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaveCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    SimpMessagingTemplate messagingTemplate;

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

        List<CallParticipant> allParticipants = callParticipantRepository.findByCallSessionId(session.getId());
        long connectedCount = allParticipants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.CONNECTED)
                .count();

        boolean isDirect = session.getChannelType() == ChannelType.DIRECT;
        boolean shouldEndCall = isDirect || connectedCount == 0;

        if (shouldEndCall && session.getStatus() != CallStatus.ENDED) {
            session.setStatus(CallStatus.ENDED);
            session.setEndedAt(LocalDateTime.now());
            if (session.getStartedAt() != null) {
                session.setDurationInSeconds(Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds());
            }
            callSessionRepository.save(session);

            // In direct calls or ended calls, also mark remaining active participants as LEFT
            for (CallParticipant p : allParticipants) {
                if (p.getStatus() == ParticipantStatus.CONNECTED || p.getStatus() == ParticipantStatus.RINGING || p.getStatus() == ParticipantStatus.INVITED) {
                    p.setStatus(ParticipantStatus.LEFT);
                    p.setLeftAt(LocalDateTime.now());
                }
            }
            callParticipantRepository.saveAll(allParticipants);
        }

        // Broadcast WebRTC signal
        WebRtcSignalType signalType = shouldEndCall ? WebRtcSignalType.END_CALL : WebRtcSignalType.LEAVE;
        WebRtcSignalResponse signalResponse = WebRtcSignalResponse.builder()
                .callSessionId(session.getId())
                .senderId(command.getCurrentUserId())
                .signalType(signalType)
                .channelType(session.getChannelType())
                .mediaType(session.getMediaType())
                .timestamp(Instant.now())
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/call/" + session.getId(), signalResponse);
            for (CallParticipant p : allParticipants) {
                messagingTemplate.convertAndSendToUser(p.getUserId().toString(), "/queue/call-signal", signalResponse);
                messagingTemplate.convertAndSend("/topic/call-user." + p.getUserId(), signalResponse);
            }
            log.info("Broadcasted {} signal for session {} on participant {} leave", signalType, session.getId(), command.getCurrentUserId());
        } catch (Exception e) {
            log.error("Failed to broadcast leave/end signal: {}", e.getMessage());
        }
    }
}
