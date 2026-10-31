package iuh.fit.callservice.presentation.controller.v1;

import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import iuh.fit.callservice.presentation.dto.request.WebRtcSignalRequest;
import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import iuh.fit.callservice.presentation.mapper.CallPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketCallController {

    SimpMessagingTemplate messagingTemplate;
    CallPresentationMapper callPresentationMapper;
    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;

    @MessageMapping("/call.signal/{callSessionId}")
    public void handleWebRtcSignal(
            @DestinationVariable UUID callSessionId,
            @Payload WebRtcSignalRequest signalRequest,
            Principal principal) {

        UUID senderId = extractSenderId(principal);
        if (senderId == null || signalRequest == null || signalRequest.getSignalType() == null) {
            log.warn("Rejected invalid or unauthenticated WebRTC signal for call session {}", callSessionId);
            return;
        }

        Optional<CallSession> sessionOpt = callSessionRepository.findById(callSessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Rejected WebRTC signal for non-existent call session {} from {}", callSessionId, senderId);
            return;
        }
        CallSession session = sessionOpt.get();

        Optional<CallParticipant> senderParticipantOpt = callParticipantRepository.findByCallSessionIdAndUserId(callSessionId, senderId);
        if (senderParticipantOpt.isEmpty() && !senderId.equals(session.getHostUserId())) {
            log.warn("Rejected WebRTC signal [{}] from non-participant {} for call session {}",
                    signalRequest.getSignalType(), senderId, callSessionId);
            return;
        }

        if (signalRequest.getSignalType() == WebRtcSignalType.END_CALL
                && session.getChannelType() == ChannelType.GROUP
                && !senderId.equals(session.getHostUserId())) {
            log.warn("Rejected unauthorized END_CALL signal from non-host {} in group call {}", senderId, callSessionId);
            return;
        }

        UUID targetUserId = signalRequest.getTargetUserId();
        if (targetUserId != null) {
            boolean targetIsParticipant = targetUserId.equals(session.getHostUserId())
                    || callParticipantRepository.findByCallSessionIdAndUserId(callSessionId, targetUserId).isPresent();
            if (!targetIsParticipant) {
                log.warn("Rejected WebRTC signal targeting non-participant {} in call {}", targetUserId, callSessionId);
                return;
            }
        }

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, callSessionId, senderId, targetUserId
        );

        // When a call ends or a participant leaves, notify everyone in the room AND all participants
        if (signalRequest.getSignalType() == WebRtcSignalType.END_CALL || signalRequest.getSignalType() == WebRtcSignalType.LEAVE) {
            messagingTemplate.convertAndSend("/topic/call/" + callSessionId, signalResponse);
            if (targetUserId != null) {
                messagingTemplate.convertAndSendToUser(targetUserId.toString(), "/queue/call-signal", signalResponse);
                messagingTemplate.convertAndSend("/topic/call-user." + targetUserId, signalResponse);
            }
            try {
                callParticipantRepository.findByCallSessionId(callSessionId).forEach(p -> {
                    if (!p.getUserId().equals(senderId) && (targetUserId == null || !p.getUserId().equals(targetUserId))) {
                        messagingTemplate.convertAndSendToUser(p.getUserId().toString(), "/queue/call-signal", signalResponse);
                        messagingTemplate.convertAndSend("/topic/call-user." + p.getUserId(), signalResponse);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to notify participants on signal {}: {}", signalRequest.getSignalType(), e.getMessage());
            }
            log.info("Broadcasted WebRTC end/leave signal [{}] from {} to call session {}", signalRequest.getSignalType(), senderId, callSessionId);
            return;
        }

        boolean groupAccept = signalRequest.getSignalType() == WebRtcSignalType.ACCEPT
                && session.getChannelType() == ChannelType.GROUP;

        if (groupAccept) {
            messagingTemplate.convertAndSend("/topic/call/" + callSessionId, signalResponse);
            log.info("Broadcast group ACCEPT from {} in call {}", senderId, callSessionId);
        } else if (targetUserId != null) {
            messagingTemplate.convertAndSendToUser(
                    targetUserId.toString(),
                    "/queue/call-signal",
                    signalResponse
            );
            messagingTemplate.convertAndSend(
                    "/topic/call-user." + targetUserId,
                    signalResponse
            );
            log.info("Sent WebRTC signal [{}] from {} to target user {}", signalRequest.getSignalType(), senderId, targetUserId);
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/call/" + callSessionId,
                    signalResponse
            );
            log.info("Broadcasted WebRTC signal [{}] from {} to call session {}", signalRequest.getSignalType(), senderId, callSessionId);
        }
    }

    @MessageMapping("/call.signal.user/{targetUserId}")
    public void handleDirectUserSignal(
            @DestinationVariable UUID targetUserId,
            @Payload WebRtcSignalRequest signalRequest,
            Principal principal) {

        UUID senderId = extractSenderId(principal);
        if (senderId == null || targetUserId == null || signalRequest == null || signalRequest.getSignalType() == null) {
            log.warn("Rejected invalid or unauthenticated direct WebRTC signal to target user {}", targetUserId);
            return;
        }

        UUID callSessionId = signalRequest.getCallSessionId();
        if (callSessionId == null) {
            log.warn("Rejected direct WebRTC signal without callSessionId from {} to {}", senderId, targetUserId);
            return;
        }

        Optional<CallSession> sessionOpt = callSessionRepository.findById(callSessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Rejected direct WebRTC signal for unknown callSessionId {} from {}", callSessionId, senderId);
            return;
        }
        CallSession session = sessionOpt.get();
        if (session.getStatus() != CallStatus.INITIATED && session.getStatus() != CallStatus.ACTIVE
                && signalRequest.getSignalType() != WebRtcSignalType.END_CALL
                && signalRequest.getSignalType() != WebRtcSignalType.REJECT
                && signalRequest.getSignalType() != WebRtcSignalType.LEAVE) {
            log.warn("Rejected direct WebRTC signal [{}] on inactive callSession {}", signalRequest.getSignalType(), callSessionId);
            return;
        }

        boolean senderIsMember = senderId.equals(session.getHostUserId())
                || callParticipantRepository.findByCallSessionIdAndUserId(callSessionId, senderId)
                .filter(p -> p.getStatus() != ParticipantStatus.DECLINED)
                .isPresent();
        boolean targetIsMember = targetUserId.equals(session.getHostUserId())
                || callParticipantRepository.findByCallSessionIdAndUserId(callSessionId, targetUserId).isPresent();

        if (!senderIsMember || !targetIsMember) {
            log.warn("Rejected unauthorized direct WebRTC signal from {} to {} on call {}", senderId, targetUserId, callSessionId);
            return;
        }

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, callSessionId, senderId, targetUserId
        );

        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/call-signal",
                signalResponse
        );
        messagingTemplate.convertAndSend(
                "/topic/call-user." + targetUserId,
                signalResponse
        );
    }

    @MessageMapping("/live.signal/{postId}")
    public void handleLiveStreamSignal(
            @DestinationVariable String postId,
            @Payload WebRtcSignalRequest signalRequest,
            Principal principal) {

        UUID senderId = extractSenderId(principal);
        if (senderId == null || postId == null || postId.isBlank() || signalRequest == null || signalRequest.getSignalType() == null) {
            log.warn("Rejected invalid or unauthenticated live stream WebRTC signal for post {}", postId);
            return;
        }

        UUID postUuid = null;
        try {
            postUuid = UUID.fromString(postId);
        } catch (Exception ignored) {
        }

        WebRtcSignalType signalType = signalRequest.getSignalType();
        if (signalType == WebRtcSignalType.END_CALL
                || signalType == WebRtcSignalType.VIEWER_COUNT
                || signalType == WebRtcSignalType.OFFER) {
            if (postUuid == null) {
                log.warn("Rejected host-only live signal [{}] for non-UUID postId {} from {}", signalType, postId, senderId);
                return;
            }
            Optional<CallSession> liveSessionOpt = callSessionRepository.findFirstByConversationIdOrderByStartedAtDesc(postUuid);
            if (liveSessionOpt.isEmpty() || !senderId.equals(liveSessionOpt.get().getHostUserId())) {
                log.warn("Rejected unauthorized host-only live signal [{}] for post {} from non-host {}", signalType, postId, senderId);
                return;
            }
            if (signalType == WebRtcSignalType.VIEWER_COUNT && signalRequest.getViewerCount() != null && signalRequest.getViewerCount() < 0) {
                signalRequest.setViewerCount(0);
            }
        }

        UUID targetUserId = signalRequest.getTargetUserId();

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, postUuid, senderId, targetUserId
        );

        // Dedicated live stream topic - totally isolated from phone/video calls
        messagingTemplate.convertAndSend("/topic/live/" + postId, signalResponse);
        log.info("Broadcasted Live Stream WebRTC signal [{}] for post {}", signalType, postId);
    }

    private UUID extractSenderId(Principal principal) {
        if (principal != null) {
            if (principal instanceof AbstractAuthenticationToken token) {
                if (token.getPrincipal() instanceof Jwt jwt) {
                    try {
                        return UUID.fromString(jwt.getSubject());
                    } catch (Exception ignored) {}
                }
                if (token.getCredentials() instanceof Jwt jwt) {
                    try {
                        return UUID.fromString(jwt.getSubject());
                    } catch (Exception ignored) {}
                }
            }
            try {
                return UUID.fromString(principal.getName());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
