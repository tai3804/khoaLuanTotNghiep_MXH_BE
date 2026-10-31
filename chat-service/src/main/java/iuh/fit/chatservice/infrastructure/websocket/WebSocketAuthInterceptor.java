package iuh.fit.chatservice.infrastructure.websocket;

import iuh.fit.chatservice.application.service.UserPresenceService;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtUtil jwtUtil;
    private final RedisCacheService redisCacheService;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserPresenceService userPresenceService;

    public WebSocketAuthInterceptor(
            JwtDecoder jwtDecoder,
            JwtUtil jwtUtil,
            RedisCacheService redisCacheService,
            ConversationMemberRepository conversationMemberRepository,
            @Lazy UserPresenceService userPresenceService
    ) {
        this.jwtDecoder = jwtDecoder;
        this.jwtUtil = jwtUtil;
        this.redisCacheService = redisCacheService;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userPresenceService = userPresenceService;
    }

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
                log.warn("WebSocket CONNECT attempted without token");
                throw new MessageDeliveryException("Unauthorized STOMP connection: token is required");
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);
                validateJwt(jwt);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(jwt, null, Collections.emptyList());
                accessor.setUser(authentication);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("userId", jwt.getSubject());
                    accessor.getSessionAttributes().put("jwt", jwt);
                }
                log.info("WebSocket connection authenticated for user: {}", jwt.getSubject());
            } catch (MessageDeliveryException e) {
                throw e;
            } catch (Exception e) {
                log.error("WebSocket JWT Authentication failed: {}", e.getMessage());
                throw new MessageDeliveryException("Unauthorized STOMP connection: invalid or expired token");
            }
            return message;
        }

        if (StompCommand.SEND.equals(command)) {
            Jwt jwt = resolveAndValidateSessionJwt(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/")) {
                log.warn("Blocked client STOMP SEND to restricted destination '{}' by user {}", destination, jwt.getSubject());
                throw new MessageDeliveryException("Forbidden STOMP SEND destination");
            }
            userPresenceService.refreshActivity(jwt.getSubject());
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            Jwt jwt = resolveAndValidateSessionJwt(accessor);
            String userIdStr = jwt.getSubject();
            String destination = accessor.getDestination();
            if (destination == null || destination.isBlank()) {
                throw new MessageDeliveryException("Invalid subscription destination");
            }

            if (destination.startsWith("/topic/conversations/")) {
                UUID userUuid = parseUuidOrThrow(userIdStr, "Invalid authenticated user ID");
                UUID conversationId = extractConversationId(destination);
                boolean isActiveMember = conversationMemberRepository
                        .existsByConversationIdAndUserIdAndStatus(conversationId, userUuid, MemberStatus.ACTIVE);
                if (!isActiveMember) {
                    log.warn("Forbidden STOMP subscription by user {} to conversation topic {}", userIdStr, destination);
                    throw new MessageDeliveryException("Forbidden subscription: not an active member of this conversation");
                }
            } else if (destination.equals("/topic/presence")
                    || destination.startsWith("/topic/presence/")
                    || destination.startsWith("/user/")) {
                // Allowed presence & user-specific destinations
            } else {
                log.warn("Rejected subscription to unknown chat broker destination '{}' by user {}", destination, userIdStr);
                throw new MessageDeliveryException("Forbidden subscription destination");
            }

            userPresenceService.refreshActivity(userIdStr);
        }

        return message;
    }

    private Jwt resolveAndValidateSessionJwt(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        Jwt jwt = null;
        if (user instanceof AbstractAuthenticationToken authToken) {
            if (authToken.getPrincipal() instanceof Jwt principalJwt) {
                jwt = principalJwt;
            } else if (authToken.getCredentials() instanceof Jwt credJwt) {
                jwt = credJwt;
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

    private UUID extractConversationId(String destination) {
        String remainder = destination.substring("/topic/conversations/".length());
        int slashIdx = remainder.indexOf('/');
        String rawId = slashIdx >= 0 ? remainder.substring(0, slashIdx) : remainder;
        return parseUuidOrThrow(rawId, "Invalid conversation ID in destination");
    }

    private UUID parseUuidOrThrow(String raw, String errorMessage) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new MessageDeliveryException(errorMessage);
        }
    }
}
