package iuh.fit.callservice.application.service;

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
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallSessionCleanupService {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    SimpMessagingTemplate messagingTemplate;
    KafkaTemplate<String, Object> kafkaTemplate;

    Map<String, UUID> sessionUserMap = new ConcurrentHashMap<>();
    Map<UUID, Set<String>> userSessionsMap = new ConcurrentHashMap<>();
    Map<UUID, Instant> disconnectedUsersGraceMap = new ConcurrentHashMap<>();

    static final Duration UNANSWERED_RING_TIMEOUT = Duration.ofSeconds(60);
    static final Duration DISCONNECT_GRACE_PERIOD = Duration.ofSeconds(25);
    static final Duration MAX_CALL_DURATION = Duration.ofHours(6);

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        UUID userId = resolveUserId(event.getUser() != null ? event.getUser() : accessor.getUser(), accessor);
        if (sessionId == null || userId == null) {
            return;
        }
        sessionUserMap.put(sessionId, userId);
        userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        disconnectedUsersGraceMap.remove(userId);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        UUID userId = sessionId != null ? sessionUserMap.remove(sessionId) : null;
        if (userId == null) {
            userId = resolveUserId(event.getUser() != null ? event.getUser() : accessor.getUser(), accessor);
        }
        if (userId == null) {
            return;
        }
        Set<String> sessions = userSessionsMap.get(userId);
        if (sessions != null && sessionId != null) {
            sessions.remove(sessionId);
        }
        if (sessions == null || sessions.isEmpty()) {
            userSessionsMap.remove(userId);
            disconnectedUsersGraceMap.put(userId, Instant.now());
        }
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    @Transactional
    public void reapStaleAndDisconnectedCalls() {
        LocalDateTime now = LocalDateTime.now();
        Instant nowInstant = Instant.now();

        // 1. Clean up participants whose WebSocket disconnected beyond grace window
        for (Map.Entry<UUID, Instant> entry : disconnectedUsersGraceMap.entrySet()) {
            UUID disconnectedUserId = entry.getKey();
            Instant disconnectedAt = entry.getValue();
            if (disconnectedAt == null || Duration.between(disconnectedAt, nowInstant).compareTo(DISCONNECT_GRACE_PERIOD) < 0) {
                continue;
            }
            disconnectedUsersGraceMap.remove(disconnectedUserId);
            cleanupDisconnectedParticipantCalls(disconnectedUserId, now);
        }

        // 2. Reap stale INITIATED / ACTIVE sessions
        List<CallSession> openSessions = callSessionRepository.findByStatusIn(List.of(CallStatus.INITIATED, CallStatus.ACTIVE));
        for (CallSession session : openSessions) {
            List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
            long connectedCount = participants.stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.CONNECTED)
                    .count();
            boolean hasRingingInvitees = participants.stream()
                    .anyMatch(p -> p.getStatus() == ParticipantStatus.RINGING || p.getStatus() == ParticipantStatus.INVITED);

            boolean unansweredExpired = session.getStatus() == CallStatus.INITIATED
                    && hasRingingInvitees
                    && session.getStartedAt() != null
                    && session.getStartedAt().isBefore(now.minus(UNANSWERED_RING_TIMEOUT));

            boolean maxDurationExceeded = session.getStartedAt() != null
                    && session.getStartedAt().isBefore(now.minus(MAX_CALL_DURATION));

            boolean directPeerGone = session.getChannelType() == ChannelType.DIRECT
                    && session.getStatus() == CallStatus.ACTIVE
                    && connectedCount <= 1
                    && participants.stream().anyMatch(p -> p.getStatus() == ParticipantStatus.LEFT || p.getStatus() == ParticipantStatus.DECLINED);

            if (unansweredExpired || connectedCount == 0 || directPeerGone || maxDurationExceeded) {
                CallStatus terminalStatus = unansweredExpired ? CallStatus.MISSED : CallStatus.ENDED;
                terminateSession(session, participants, terminalStatus, now);
                if (unansweredExpired) {
                    publishMissedCallNotifications(session, participants);
                }
            }
        }
    }

    private void cleanupDisconnectedParticipantCalls(UUID disconnectedUserId, LocalDateTime now) {
        List<CallSession> activeCalls = callSessionRepository.findActiveCallSessionsByUserId(disconnectedUserId);
        for (CallSession session : activeCalls) {
            List<CallParticipant> participants = callParticipantRepository.findByCallSessionId(session.getId());
            CallParticipant userParticipant = participants.stream()
                    .filter(p -> disconnectedUserId.equals(p.getUserId()))
                    .findFirst()
                    .orElse(null);

            if (userParticipant == null || userParticipant.getStatus() != ParticipantStatus.CONNECTED) {
                continue;
            }

            userParticipant.setStatus(ParticipantStatus.LEFT);
            userParticipant.setLeftAt(now);
            callParticipantRepository.save(userParticipant);

            long remainingConnected = participants.stream()
                    .filter(p -> !disconnectedUserId.equals(p.getUserId()) && p.getStatus() == ParticipantStatus.CONNECTED)
                    .count();

            boolean shouldEndSession = session.getChannelType() == ChannelType.DIRECT
                    || remainingConnected == 0
                    || disconnectedUserId.equals(session.getHostUserId());

            if (shouldEndSession) {
                CallStatus terminalStatus = (session.getStatus() == CallStatus.INITIATED
                        && participants.stream().anyMatch(p -> p.getStatus() == ParticipantStatus.RINGING || p.getStatus() == ParticipantStatus.INVITED))
                        ? CallStatus.MISSED : CallStatus.ENDED;
                terminateSession(session, participants, terminalStatus, now);
            } else {
                broadcastSignal(session, participants, disconnectedUserId, WebRtcSignalType.LEAVE);
            }
        }
    }

    private void terminateSession(CallSession session, List<CallParticipant> participants, CallStatus terminalStatus, LocalDateTime now) {
        session.setStatus(terminalStatus);
        session.setEndedAt(now);
        if (terminalStatus == CallStatus.MISSED || session.getStartedAt() == null) {
            session.setDurationInSeconds(0);
        } else {
            session.setDurationInSeconds(Math.max(0, Duration.between(session.getStartedAt(), now).getSeconds()));
        }
        callSessionRepository.save(session);

        for (CallParticipant p : participants) {
            if (p.getStatus() == ParticipantStatus.CONNECTED
                    || p.getStatus() == ParticipantStatus.RINGING
                    || p.getStatus() == ParticipantStatus.INVITED) {
                p.setStatus(ParticipantStatus.LEFT);
                p.setLeftAt(now);
            }
        }
        callParticipantRepository.saveAll(participants);

        broadcastSignal(session, participants, session.getHostUserId(), WebRtcSignalType.END_CALL);
        log.info("Auto-terminated stale/disconnected call session {} with status {}", session.getId(), terminalStatus);
    }

    private void broadcastSignal(CallSession session, List<CallParticipant> participants, UUID senderId, WebRtcSignalType signalType) {
        WebRtcSignalResponse signal = WebRtcSignalResponse.builder()
                .callSessionId(session.getId())
                .senderId(senderId)
                .signalType(signalType)
                .channelType(session.getChannelType())
                .mediaType(session.getMediaType())
                .timestamp(Instant.now())
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/call/" + session.getId(), signal);
            for (CallParticipant p : participants) {
                messagingTemplate.convertAndSendToUser(p.getUserId().toString(), "/queue/call-signal", signal);
                messagingTemplate.convertAndSend("/topic/call-user." + p.getUserId(), signal);
            }
        } catch (Exception e) {
            log.debug("Failed to broadcast cleanup signal {} for call {}: {}", signalType, session.getId(), e.getMessage());
        }
    }

    private void publishMissedCallNotifications(CallSession session, List<CallParticipant> participants) {
        for (CallParticipant p : participants) {
            if (p.getUserId() != null && !p.getUserId().equals(session.getHostUserId())) {
                try {
                    Map<String, Object> event = new HashMap<>();
                    event.put("recipientId", p.getUserId().toString());
                    event.put("actorId", session.getHostUserId().toString());
                    event.put("type", "CALL_MISSED");
                    event.put("title", "Cuộc gọi nhỡ");
                    event.put("content", "Bạn có một cuộc gọi nhỡ");
                    event.put("targetId", session.getId().toString());
                    event.put("targetUrl", "/calls");
                    kafkaTemplate.send("notification.in-app.send", event);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private UUID resolveUserId(Principal principal, StompHeaderAccessor accessor) {
        if (principal instanceof AbstractAuthenticationToken authToken) {
            if (authToken.getCredentials() instanceof Jwt jwt) {
                try {
                    return UUID.fromString(jwt.getSubject());
                } catch (Exception ignored) {}
            }
            if (authToken.getPrincipal() instanceof Jwt jwt) {
                try {
                    return UUID.fromString(jwt.getSubject());
                } catch (Exception ignored) {}
            }
        }
        if (principal != null && principal.getName() != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (Exception ignored) {}
        }
        if (accessor != null && accessor.getSessionAttributes() != null) {
            Object raw = accessor.getSessionAttributes().get("userId");
            if (raw != null) {
                try {
                    return UUID.fromString(raw.toString());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}

