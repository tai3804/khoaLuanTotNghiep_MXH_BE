package iuh.fit.callservice.application.features.call.commands.initiate_call;

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

import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InitiateCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;
    SimpMessagingTemplate messagingTemplate;

    @Transactional
    public InitiateCallResult handle(InitiateCallCommand command) {
        // Check if user is already in an active call
        Optional<CallSession> activeCallOpt = callSessionRepository.findActiveCallSessionByUserId(command.getCurrentUserId());
        if (activeCallOpt.isPresent()) {
            throw new BusinessException(CallServiceErrorCode.USER_ALREADY_IN_CALL);
        }

        CallSession session = CallSession.builder()
                .channelType(command.getChannelType())
                .mediaType(command.getMediaType())
                .conversationId(command.getConversationId())
                .hostUserId(command.getCurrentUserId())
                .status(CallStatus.INITIATED)
                .startedAt(LocalDateTime.now())
                .build();
        session = callSessionRepository.save(session);

        List<CallParticipant> participantsToSave = new ArrayList<>();

        // Add Host Participant
        CallParticipant hostParticipant = CallParticipant.builder()
                .callSessionId(session.getId())
                .userId(command.getCurrentUserId())
                .role(ParticipantRole.HOST)
                .status(ParticipantStatus.CONNECTED)
                .joinedAt(LocalDateTime.now())
                .build();
        participantsToSave.add(hostParticipant);

        // Add Target Participants & Notify via WebSocket STOMP
        if (command.getTargetUserIds() != null) {
            for (UUID targetId : command.getTargetUserIds()) {
                if (targetId.equals(command.getCurrentUserId())) {
                    continue;
                }
                CallParticipant participant = CallParticipant.builder()
                        .callSessionId(session.getId())
                        .userId(targetId)
                        .role(ParticipantRole.PARTICIPANT)
                        .status(ParticipantStatus.RINGING)
                        .build();
                participantsToSave.add(participant);

                // Real-time Push: INCOMING_CALL signal to target user queue
                WebRtcSignalResponse incomingSignal = WebRtcSignalResponse.builder()
                        .callSessionId(session.getId())
                        .senderId(command.getCurrentUserId())
                        .targetUserId(targetId)
                        .signalType(WebRtcSignalType.INCOMING_CALL)
                        .mediaType(session.getMediaType())
                        .timestamp(Instant.now())
                        .build();

                try {
                    messagingTemplate.convertAndSendToUser(
                            targetId.toString(),
                            "/queue/call-signal",
                            incomingSignal
                    );
                } catch (Exception ignored) {}
            }
        }

        participantsToSave = callParticipantRepository.saveAll(participantsToSave);

        List<InitiateCallResult.ParticipantResult> participantResults = participantsToSave.stream()
                .map(callFeatureMapper::toInitiateParticipantResult)
                .toList();

        return callFeatureMapper.toInitiateCallResult(session, participantResults);
    }
}
