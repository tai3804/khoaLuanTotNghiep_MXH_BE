package iuh.fit.callservice.infrastructure.websocket;

import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketCallAuthInterceptor implements ChannelInterceptor {

    JwtDecoder jwtDecoder;
    JwtUtil jwtUtil;
    RedisCacheService redisCacheService;
    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;

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

            if (token == null || token.isBlank()) {
                log.warn("WebSocket Call CONNECT attempted without token");
                throw new MessageDeliveryException("Unauthorized STOMP connection: token is required");
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);
                validateJwt(jwt);

                String userId = jwt.getSubject();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, jwt, Collections.emptyList());
                accessor.setUser(authentication);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.getSessionAttributes().put("jwt", jwt);
                }
                log.info("WebSocket Call connection authenticated for user: {}", userId);
            } catch (MessageDeliveryException e) {
                throw e;
            } catch (Exception e) {
                log.error("WebSocket Call JWT Authentication failed: {}", e.getMessage());
                throw new MessageDeliveryException("Unauthorized STOMP connection: invalid or expired token");
            }
            return message;
        }

        if (StompCommand.SEND.equals(command)) {
            Jwt jwt = resolveAndValidateSessionJwt(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/")) {
                log.warn("Blocked client Call STOMP SEND to restricted destination '{}' by user {}", destination, jwt.getSubject());
                throw new MessageDeliveryException("Forbidden STOMP SEND destination");
            }
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            Jwt jwt = resolveAndValidateSessionJwt(accessor);
            String currentUserId = jwt.getSubject();
            String destination = accessor.getDestination();
            if (destination == null || destination.isBlank()) {
                throw new MessageDeliveryException("Invalid subscription destination");
            }

            if (destination.startsWith("/user/")) {
                return message;
            }

            if (destination.startsWith("/topic/call-user.")) {
                String targetUserId = destination.substring("/topic/call-user.".length());
                if (currentUserId == null || !currentUserId.equalsIgnoreCase(targetUserId)) {
                    log.warn("User {} attempted to subscribe to call signals of user {}", currentUserId, targetUserId);
                    throw new MessageDeliveryException("Forbidden subscription: cannot subscribe to another user's call signals");
                }
                return message;
            }

            if (destination.startsWith("/topic/user/") && destination.endsWith("/call")) {
                String middle = destination.substring("/topic/user/".length(), destination.length() - "/call".length());
                if (currentUserId == null || !currentUserId.equalsIgnoreCase(middle)) {
                    throw new MessageDeliveryException("Forbidden subscription: cannot subscribe to another user's call signals");
                }
                return message;
            }

            if (destination.startsWith("/topic/call/")) {
                String rawSessionId = destination.substring("/topic/call/".length());
                int slashIdx = rawSessionId.indexOf('/');
                if (slashIdx >= 0) {
                    rawSessionId = rawSessionId.substring(0, slashIdx);
                }
                UUID callSessionId = parseUuidOrThrow(rawSessionId, "Invalid call session ID");
                UUID userUuid = parseUuidOrThrow(currentUserId, "Invalid user ID");

                CallSession session = callSessionRepository.findById(callSessionId)
                        .orElseThrow(() -> new MessageDeliveryException("Call session not found"));

                boolean isAllowed = userUuid.equals(session.getHostUserId())
                        || callParticipantRepository.findByCallSessionIdAndUserId(callSessionId, userUuid).isPresent();
                if (!isAllowed) {
                    log.warn("User {} attempted unauthorized subscription to call room {}", currentUserId, callSessionId);
                    throw new MessageDeliveryException("Forbidden subscription: not a participant in this call session");
                }
                return message;
            }

            if (destination.startsWith("/topic/live/")) {
                // Live stream room topic for authenticated viewers and broadcaster
                return message;
            }

            log.warn("Rejected subscription to unknown call broker destination '{}' by user {}", destination, currentUserId);
            throw new MessageDeliveryException("Forbidden subscription destination");
        }

        return message;
    }

    private Jwt resolveAndValidateSessionJwt(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        Jwt jwt = null;
        if (user instanceof AbstractAuthenticationToken authToken) {
            if (authToken.getCredentials() instanceof Jwt credJwt) {
                jwt = credJwt;
            } else if (authToken.getPrincipal() instanceof Jwt principalJwt) {
                jwt = principalJwt;
            }
        }
        if (jwt == null && accessor.getSessionAttributes() != null) {
            Object cached = accessor.getSessionAttributes().get("jwt");
            if (cached instanceof Jwt sessionJwt) {
                jwt = sessionJwt;
            }
        }
        if (jwt == null) {
            throw new MessageDeliveryException("Unauthorized STOMP frame: authentication required");
        }
        validateJwt(jwt);
        return jwt;
    }

    private void validateJwt(Jwt jwt) {
        if (!jwtUtil.isTokenValid(jwt)) {
            throw new MessageDeliveryException("Unauthorized STOMP frame: token has expired");
        }
        if (jwtUtil.isTokenRevoked(jwt, redisCacheService)) {
            throw new MessageDeliveryException("Unauthorized STOMP frame: token has been revoked");
        }
    }

    private UUID parseUuidOrThrow(String raw, String errorMessage) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new MessageDeliveryException(errorMessage);
        }
    }
}
