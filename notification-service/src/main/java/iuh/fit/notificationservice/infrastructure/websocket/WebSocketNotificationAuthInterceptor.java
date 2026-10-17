package iuh.fit.notificationservice.infrastructure.websocket;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketNotificationAuthInterceptor implements ChannelInterceptor {

    JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = accessor.getFirstNativeHeader("access_token");
            }

            if (token != null && !token.isBlank()) {
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(jwt, null, Collections.emptyList());
                    accessor.setUser(authentication);
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("userId", jwt.getSubject());
                    }
                    log.info("WebSocket notification connection authenticated for user: {}", jwt.getSubject());
                } catch (Exception e) {
                    log.error("WebSocket notification JWT authentication failed: {}", e.getMessage());
                    throw new MessageDeliveryException("Unauthorized STOMP connection: invalid or expired token");
                }
            } else {
                log.warn("WebSocket notification CONNECT attempted without token");
                throw new MessageDeliveryException("Unauthorized STOMP connection: token is required");
            }
        }

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null) {
                if (accessor.getUser() == null) {
                    log.warn("Unauthorized subscription attempt to notification destination: {}", destination);
                    throw new MessageDeliveryException("Unauthorized subscription: authentication required");
                }

                String currentUserId = null;
                if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
                    if (auth.getPrincipal() instanceof Jwt jwt) {
                        currentUserId = jwt.getSubject();
                    } else if (auth.getPrincipal() instanceof String s) {
                        currentUserId = s;
                    }
                }

                if (destination.startsWith("/topic/notifications.count.")) {
                    String targetUserId = destination.substring("/topic/notifications.count.".length());
                    if (currentUserId == null || !currentUserId.equalsIgnoreCase(targetUserId)) {
                        log.warn("User {} attempted to subscribe to notifications count of {}", currentUserId, targetUserId);
                        throw new MessageDeliveryException("Forbidden subscription: cannot subscribe to another user's notifications count");
                    }
                } else if (destination.startsWith("/topic/notifications.")) {
                    String targetUserId = destination.substring("/topic/notifications.".length());
                    if (currentUserId == null || !currentUserId.equalsIgnoreCase(targetUserId)) {
                        log.warn("User {} attempted to subscribe to notifications feed of {}", currentUserId, targetUserId);
                        throw new MessageDeliveryException("Forbidden subscription: cannot subscribe to another user's notifications feed");
                    }
                }
            }
        }

        return message;
    }
}
