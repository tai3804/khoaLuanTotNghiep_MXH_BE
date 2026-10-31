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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InitiateCallCommandHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;
    SimpMessagingTemplate messagingTemplate;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public InitiateCallResult handle(InitiateCallCommand command) {
        Set<UUID> validTargetIds = new LinkedHashSet<>();
        if (command.getTargetUserIds() != null) {
            for (UUID targetId : command.getTargetUserIds()) {
                if (targetId != null && !targetId.equals(command.getCurrentUserId())) {
                    validTargetIds.add(targetId);
                }
            }
        }

        if (command.getChannelType() == ChannelType.DIRECT && validTargetIds.isEmpty()) {
            throw new BusinessException(CallServiceErrorCode.CANNOT_CALL_SELF);
        }

        // An unanswered call must not lock the caller forever.  The browser can
        // be closed or lose its connection before it sends the timeout/end API.
        // Expire only old INITIATED sessions; genuinely connected calls remain
        // protected by the normal active-call rule.
        Optional<CallSession> activeCallOpt = callSessionRepository.findActiveCallSessionsByUserId(command.getCurrentUserId())
                .stream().findFirst();
        if (activeCallOpt.isPresent()) {
            CallSession activeCall = activeCallOpt.get();
            boolean unansweredAndExpired = activeCall.getStatus() == CallStatus.INITIATED
                    && activeCall.getStartedAt() != null
                    && activeCall.getStartedAt().isBefore(LocalDateTime.now().minusSeconds(40));

            if (command.getChannelType() == ChannelType.GROUP || unansweredAndExpired || activeCall.getHostUserId().equals(command.getCurrentUserId())) {
                activeCall.setStatus(unansweredAndExpired ? CallStatus.MISSED : CallStatus.ENDED);
                activeCall.setEndedAt(LocalDateTime.now());
                activeCall.setDurationInSeconds(0);
                callSessionRepository.save(activeCall);

                List<CallParticipant> staleParticipants = callParticipantRepository.findByCallSessionId(activeCall.getId());
                staleParticipants.stream()
                        .filter(participant -> participant.getStatus() == ParticipantStatus.INVITED
                                || participant.getStatus() == ParticipantStatus.RINGING
                                || participant.getStatus() == ParticipantStatus.CONNECTED)
                        .forEach(participant -> {
                            participant.setStatus(ParticipantStatus.LEFT);
                            participant.setLeftAt(LocalDateTime.now());
                        });
                callParticipantRepository.saveAll(staleParticipants);
            } else {
                throw new BusinessException(CallServiceErrorCode.USER_ALREADY_IN_CALL);
            }
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
        List<WebRtcSignalResponse> incomingSignals = new ArrayList<>();

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
        for (UUID targetId : validTargetIds) {
            CallParticipant participant = CallParticipant.builder()
                    .callSessionId(session.getId())
                    .userId(targetId)
                    .role(ParticipantRole.PARTICIPANT)
                    .status(ParticipantStatus.RINGING)
                    .build();
            participantsToSave.add(participant);

            WebRtcSignalResponse incomingSignal = WebRtcSignalResponse.builder()
                    .callSessionId(session.getId())
                    .senderId(command.getCurrentUserId())
                    .targetUserId(targetId)
                    .signalType(WebRtcSignalType.INCOMING_CALL)
                    .mediaType(session.getMediaType())
                    .channelType(session.getChannelType())
                    .timestamp(Instant.now())
                    .build();

            incomingSignals.add(incomingSignal);
        }

        participantsToSave = callParticipantRepository.saveAll(participantsToSave);

        // Deliver only after the session and participant rows are committed.
        // Otherwise a fast receiver can press Answer before its invitation is
        // visible to the join endpoint.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                incomingSignals.forEach(signal -> {
                    notifyIncomingCall(signal);
                    publishCallNotification(signal, "CALL_INCOMING", "Cuộc gọi đến", "Bạn có một cuộc gọi đến");
                });
            }
        });

        List<InitiateCallResult.ParticipantResult> participantResults = participantsToSave.stream()
                .filter(Objects::nonNull)
                .map(callFeatureMapper::toInitiateParticipantResult)
                .toList();

        return callFeatureMapper.toInitiateCallResult(session, participantResults);
    }

    private void notifyIncomingCall(WebRtcSignalResponse signal) {
        try {
            messagingTemplate.convertAndSendToUser(
                    signal.getTargetUserId().toString(),
                    "/queue/call-signal",
                    signal
            );
            messagingTemplate.convertAndSend(
                    "/topic/call-user." + signal.getTargetUserId(), signal);
        } catch (Exception ignored) {
            // The receiver may be offline; their client recovers a ringing call
            // through GET /calls/active after reconnecting.
        }
    }

    private void publishCallNotification(WebRtcSignalResponse signal, String type, String title, String content) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("recipientId", signal.getTargetUserId().toString());
            event.put("actorId", signal.getSenderId().toString());
            event.put("type", type);
            event.put("title", title);
            event.put("content", content);
            event.put("targetId", signal.getCallSessionId().toString());
            event.put("targetUrl", "/calls");
            kafkaTemplate.send("notification.in-app.send", event);
        } catch (Exception ignored) {
            // Calling remains available when the notification broker is offline.
        }
    }
}
