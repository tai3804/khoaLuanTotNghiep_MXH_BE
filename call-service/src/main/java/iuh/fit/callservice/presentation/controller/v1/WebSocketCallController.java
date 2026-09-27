package iuh.fit.callservice.presentation.controller.v1;

import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import iuh.fit.callservice.presentation.dto.request.WebRtcSignalRequest;
import iuh.fit.callservice.presentation.dto.response.WebRtcSignalResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import iuh.fit.callservice.presentation.mapper.CallPresentationMapper;
import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;

import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;

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

        UUID senderId = extractSenderId(principal, signalRequest);
        UUID targetUserId = signalRequest.getTargetUserId();

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
                    if (senderId == null || !p.getUserId().equals(senderId)) {
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
                && callSessionRepository.findById(callSessionId)
                .map(session -> session.getChannelType() == ChannelType.GROUP)
                .orElse(false);

        // An ACCEPT in a group is visible to the whole call room. Existing
        // participants then offer a peer connection to the member who just
        // joined, producing a mesh rather than host-only audio/video.
        if (groupAccept) {
            messagingTemplate.convertAndSend("/topic/call/" + callSessionId, signalResponse);
            log.info("Broadcast group ACCEPT from {} in call {}", senderId, callSessionId);
        } else if (targetUserId != null) {
            // Send 1-1 P2P WebRTC signal directly to target user queue and fallback topic
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
            // Broadcast to all participants in group call room
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

        UUID senderId = extractSenderId(principal, signalRequest);
        UUID callSessionId = signalRequest.getCallSessionId();

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

        UUID senderId = extractSenderId(principal, signalRequest);
        UUID targetUserId = signalRequest.getTargetUserId();

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, null, senderId, targetUserId
        );

        // Dedicated live stream topic - totally isolated from phone/video calls
        messagingTemplate.convertAndSend("/topic/live/" + postId, signalResponse);
        log.info("Broadcasted Live Stream WebRTC signal [{}] for post {}", signalRequest.getSignalType(), postId);
    }

    private UUID extractSenderId(Principal principal, WebRtcSignalRequest signalRequest) {
        if (principal != null) {
            if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
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
        if (signalRequest != null && signalRequest.getSenderId() != null) {
            return signalRequest.getSenderId();
        }
        return null;
    }
}

