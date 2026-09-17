package iuh.fit.chatservice.application.service;

import iuh.fit.chatservice.presentation.dto.response.UserPresenceResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPresenceService {

    RedisTemplate<String, Object> redisTemplate;
    SimpMessagingTemplate messagingTemplate;

    public static final String ONLINE_KEY_PREFIX = "user:online:";
    public static final String LAST_ACTIVE_KEY_PREFIX = "user:lastActiveAt:";
    public static final String PRESENCE_TOPIC = "/topic/presence";

    // Track active sessions per user to avoid marking offline when user has multiple tabs/devices
    Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    Map<String, Set<String>> userSessionsMap = new ConcurrentHashMap<>();

    public void handleConnect(String userId, String sessionId) {
        if (userId == null || userId.isBlank()) return;

        sessionUserMap.put(sessionId, userId);
        userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        // Update Redis key: user:online:{userId} -> "true"
        redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "true");

        LocalDateTime now = LocalDateTime.now();
        redisTemplate.opsForValue().set(LAST_ACTIVE_KEY_PREFIX + userId, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        log.info("[UC-CH06][Presence] User {} connected (Session: {}). Marked as ONLINE in Redis.", userId, sessionId);

        // Broadcast presence update to WebSocket subscribers
        try {
            UserPresenceResponse response = UserPresenceResponse.builder()
                    .userId(UUID.fromString(userId))
                    .online(true)
                    .lastActiveAt(now)
                    .build();
            messagingTemplate.convertAndSend(PRESENCE_TOPIC, response);
            messagingTemplate.convertAndSend(PRESENCE_TOPIC + "/" + userId, response);
        } catch (Exception e) {
            log.error("[UC-CH06][Presence] Error broadcasting connect presence for user {}: {}", userId, e.getMessage());
        }
    }

    public void handleDisconnect(String sessionId, String fallbackUserId) {
        String userId = sessionUserMap.remove(sessionId);
        if (userId == null) {
            userId = fallbackUserId;
        }
        if (userId == null || userId.isBlank()) return;

        Set<String> sessions = userSessionsMap.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
        }

        boolean isStillConnected = sessions != null && !sessions.isEmpty();
        if (!isStillConnected) {
            userSessionsMap.remove(userId);

            // Update Redis key: user:online:{userId} -> "false"
            redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "false");

            LocalDateTime now = LocalDateTime.now();
            redisTemplate.opsForValue().set(LAST_ACTIVE_KEY_PREFIX + userId, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            log.info("[UC-CH06][Presence] User {} disconnected (Session: {}). Marked as OFFLINE in Redis.", userId, sessionId);

            // Broadcast presence update to WebSocket subscribers
            try {
                UserPresenceResponse response = UserPresenceResponse.builder()
                        .userId(UUID.fromString(userId))
                        .online(false)
                        .lastActiveAt(now)
                        .build();
                messagingTemplate.convertAndSend(PRESENCE_TOPIC, response);
                messagingTemplate.convertAndSend(PRESENCE_TOPIC + "/" + userId, response);
            } catch (Exception e) {
                log.error("[UC-CH06][Presence] Error broadcasting disconnect presence for user {}: {}", userId, e.getMessage());
            }
        } else {
            log.info("[UC-CH06][Presence] User {} disconnected session {}, but still has {} active sessions.",
                    userId, sessionId, sessions.size());
        }
    }

    public UserPresenceResponse getPresence(UUID userId) {
        if (userId == null) return null;
        String userIdStr = userId.toString();

        Object onlineObj = redisTemplate.opsForValue().get(ONLINE_KEY_PREFIX + userIdStr);
        boolean isOnline = false;
        if (onlineObj != null) {
            isOnline = "true".equalsIgnoreCase(onlineObj.toString()) || Boolean.TRUE.equals(onlineObj);
        }

        Object lastActiveObj = redisTemplate.opsForValue().get(LAST_ACTIVE_KEY_PREFIX + userIdStr);
        LocalDateTime lastActiveAt = null;
        if (lastActiveObj != null) {
            try {
                lastActiveAt = LocalDateTime.parse(lastActiveObj.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignored) {}
        }

        return UserPresenceResponse.builder()
                .userId(userId)
                .online(isOnline)
                .lastActiveAt(lastActiveAt)
                .build();
    }

    public Map<UUID, UserPresenceResponse> getBatchPresence(List<UUID> userIds) {
        Map<UUID, UserPresenceResponse> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return result;

        for (UUID uid : userIds) {
            if (uid != null) {
                result.put(uid, getPresence(uid));
            }
        }
        return result;
    }
}
