package iuh.fit.notificationservice.infrastructure.websocket;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
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
    JwtUtil jwtUtil;
    RedisCacheService redisCacheService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = accessor.getFirstNativeHeader("access_token");
            }

            if (token != null && !token.isBlank()) {
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    validateActiveJwt(jwt);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(jwt, null, Collections.emptyList());
                    accessor.setUser(authentication);
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("userId", jwt.getSubject());
                    }
                    log.info("WebSocket notification connection authenticated for user: {}", jwt.getSubject());
                } catch (MessageDeliveryException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("WebSocket notification JWT authentication failed: {}", e.getMessage());
                    throw new MessageDeliveryException("Unauthorized STOMP connection: invalid or expired token");
                }
            } else {
                log.warn("WebSocket notification CONNECT attempted without token");
                throw new MessageDeliveryException("Unauthorized STOMP connection: token is required");
            }
        } else if (StompCommand.SEND.equals(command)) {
            Jwt jwt = requireAuthenticatedJwt(accessor);
            validateActiveJwt(jwt);

            String destination = accessor.getDestination();
            if (destination != null && (destination.startsWith("/topic") || destination.startsWith("/queue"))) {
                log.warn("Blocked client STOMP SEND directly to broker destination {} by user {}", destination, jwt.getSubject());
                throw new MessageDeliveryException("Forbidden: clients cannot publish directly to broker topics or queues");
            }
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            Jwt jwt = requireAuthenticatedJwt(accessor);
            validateActiveJwt(jwt);

            String currentUserId = jwt.getSubject();
            String destination = accessor.getDestination();
            if (destination != null) {
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
                } else if (!destination.startsWith("/user/")) {
                    log.warn("User {} attempted to subscribe to unauthorized destination {}", currentUserId, destination);
                    throw new MessageDeliveryException("Forbidden subscription destination");
                }
            }
        }

        return message;
    }

    private Jwt requireAuthenticatedJwt(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new MessageDeliveryException("Unauthorized STOMP frame: authentication required");
    }

    private void validateActiveJwt(Jwt jwt) {
        if (!jwtUtil.isTokenValid(jwt)) {
            throw new MessageDeliveryException("Unauthorized STOMP frame: token expired");
        }
        if (jwtUtil.isTokenRevoked(jwt, redisCacheService)) {
            throw new MessageDeliveryException("Unauthorized STOMP frame: token revoked");
        }
    }
}
