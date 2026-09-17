package iuh.fit.chatservice.infrastructure.websocket;

import iuh.fit.chatservice.application.service.UserPresenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketEventListener {

    UserPresenceService userPresenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = event.getUser() != null ? event.getUser() : accessor.getUser();

        String userId = extractUserId(principal);
        if (userId == null && accessor.getSessionAttributes() != null) {
            Object attr = accessor.getSessionAttributes().get("userId");
            if (attr != null) {
                userId = attr.toString();
            }
        }

        log.info("[UC-CH06][WebSocket] SessionConnectedEvent: sessionId={}, userId={}", sessionId, userId);
        if (userId != null && !userId.isBlank()) {
            userPresenceService.handleConnect(userId, sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = event.getUser() != null ? event.getUser() : accessor.getUser();

        String userId = extractUserId(principal);
        if (userId == null && accessor.getSessionAttributes() != null) {
            Object attr = accessor.getSessionAttributes().get("userId");
            if (attr != null) {
                userId = attr.toString();
            }
        }

        log.info("[UC-CH06][WebSocket] SessionDisconnectEvent: sessionId={}, userId={}", sessionId, userId);
        userPresenceService.handleDisconnect(sessionId, userId);
    }

    private String extractUserId(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof AbstractAuthenticationToken token) {
            if (token.getPrincipal() instanceof Jwt jwt) {
                return jwt.getSubject();
            }
        }
        return principal.getName();
    }
}
