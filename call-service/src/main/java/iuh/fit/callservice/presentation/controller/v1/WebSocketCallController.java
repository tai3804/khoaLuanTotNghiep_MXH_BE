package iuh.fit.callservice.presentation.controller.v1;

import iuh.fit.callservice.presentation.dto.request.WebRtcSignalRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketCallController {

    SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/call.signal/{callSessionId}")
    public void handleWebRtcSignal(
            @DestinationVariable UUID callSessionId,
            @Payload WebRtcSignalRequest signalRequest,
            Principal principal) {
        
        String senderId = principal != null ? principal.getName() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("callSessionId", callSessionId);
        payload.put("senderId", senderId);
        payload.put("targetUserId", signalRequest.getTargetUserId());
        payload.put("signalType", signalRequest.getSignalType());
        payload.put("sdp", signalRequest.getSdp());
        payload.put("candidate", signalRequest.getCandidate());

        if (signalRequest.getTargetUserId() != null) {
            messagingTemplate.convertAndSendToUser(
                    signalRequest.getTargetUserId().toString(),
                    "/queue/call-signal",
                    (Object) payload
            );
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/call/" + callSessionId,
                    (Object) payload
            );
        }
    }
}
