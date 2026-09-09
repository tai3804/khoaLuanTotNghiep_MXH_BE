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

@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketCallController {

    SimpMessagingTemplate messagingTemplate;
    CallPresentationMapper callPresentationMapper;

    @MessageMapping("/call.signal/{callSessionId}")
    public void handleWebRtcSignal(
            @DestinationVariable UUID callSessionId,
            @Payload WebRtcSignalRequest signalRequest,
            Principal principal) {

        UUID senderId = extractSenderId(principal);
        UUID targetUserId = signalRequest.getTargetUserId();

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, callSessionId, senderId, targetUserId
        );

        if (targetUserId != null) {
            // Send 1-1 P2P WebRTC signal directly to target user queue
            messagingTemplate.convertAndSendToUser(
                    targetUserId.toString(),
                    "/queue/call-signal",
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

        UUID senderId = extractSenderId(principal);
        UUID callSessionId = signalRequest.getCallSessionId();

        WebRtcSignalResponse signalResponse = callPresentationMapper.toSignalResponse(
                signalRequest, callSessionId, senderId, targetUserId
        );

        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/call-signal",
                signalResponse
        );
    }

    private UUID extractSenderId(Principal principal) {
        if (principal == null) return null;

        if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
            if (token.getPrincipal() instanceof Jwt jwt) {
                try {
                    return UUID.fromString(jwt.getSubject());
                } catch (Exception ignored) {}
            }
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (Exception e) {
            log.error("Could not parse UUID from principal: {}", principal.getName());
            return null;
        }
    }
}

