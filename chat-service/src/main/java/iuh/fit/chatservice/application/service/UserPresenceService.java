package iuh.fit.chatservice.application.service;

import iuh.fit.chatservice.presentation.dto.response.UserPresenceResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    static final Duration ONLINE_TTL = Duration.ofMinutes(5);
    static final Duration LAST_ACTIVE_TTL = Duration.ofDays(30);

    // Track active sessions per user to avoid marking offline when user has multiple tabs/devices
    Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    Map<String, Set<String>> userSessionsMap = new ConcurrentHashMap<>();

    public void handleConnect(String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null) return;

        sessionUserMap.put(sessionId, userId);
        userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        LocalDateTime now = LocalDateTime.now();
        try {
            // Store online status with a bounded TTL so crashed instances never leave ghost ONLINE states forever
            redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "true", ONLINE_TTL);
            redisTemplate.opsForValue().set(
                    LAST_ACTIVE_KEY_PREFIX + userId,
                    now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    LAST_ACTIVE_TTL
            );
        } catch (Exception e) {
            log.warn("[UC-CH06][Presence] Redis unavailable on connect: {}", e.getMessage());
        }

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

    public void refreshActivity(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "true", ONLINE_TTL);
            redisTemplate.opsForValue().set(
                    LAST_ACTIVE_KEY_PREFIX + userId,
                    now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    LAST_ACTIVE_TTL
            );
        } catch (Exception e) {
            log.debug("[UC-CH06][Presence] Redis unavailable on activity refresh: {}", e.getMessage());
        }
    }

    /**
     * Periodically refreshes the 5-minute online TTL for all WebSocket sessions
     * actively connected to this instance. If the instance crashes without firing
     * SessionDisconnectEvent, keys expire automatically after ONLINE_TTL.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void refreshConnectedUsersPresence() {
        if (userSessionsMap.isEmpty()) {
            return;
        }
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (Map.Entry<String, Set<String>> entry : userSessionsMap.entrySet()) {
            Set<String> sessions = entry.getValue();
            if (sessions != null && !sessions.isEmpty()) {
                String userId = entry.getKey();
                try {
                    redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "true", ONLINE_TTL);
                    redisTemplate.opsForValue().set(LAST_ACTIVE_KEY_PREFIX + userId, nowStr, LAST_ACTIVE_TTL);
                } catch (Exception e) {
                    log.debug("[UC-CH06][Presence] Failed to refresh TTL for user {}: {}", userId, e.getMessage());
                }
            }
        }
    }

    public void handleDisconnect(String sessionId, String fallbackUserId) {
        String userId = sessionId != null ? sessionUserMap.remove(sessionId) : null;
        if (userId == null) {
            userId = fallbackUserId;
        }
        if (userId == null || userId.isBlank()) return;

        Set<String> sessions = userSessionsMap.get(userId);
        if (sessions != null && sessionId != null) {
            sessions.remove(sessionId);
        }

        boolean isStillConnected = sessions != null && !sessions.isEmpty();
        if (!isStillConnected) {
            userSessionsMap.remove(userId);

            LocalDateTime now = LocalDateTime.now();
            try {
                redisTemplate.delete(ONLINE_KEY_PREFIX + userId);
                redisTemplate.opsForValue().set(
                        LAST_ACTIVE_KEY_PREFIX + userId,
                        now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        LAST_ACTIVE_TTL
                );
            } catch (Exception e) {
                log.warn("[UC-CH06][Presence] Redis unavailable on disconnect: {}", e.getMessage());
            }

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

        boolean isOnline = false;
        LocalDateTime lastActiveAt = null;

        try {
            Object onlineObj = redisTemplate.opsForValue().get(ONLINE_KEY_PREFIX + userIdStr);
            if (onlineObj != null) {
                isOnline = "true".equalsIgnoreCase(onlineObj.toString()) || Boolean.TRUE.equals(onlineObj);
            }

            Object lastActiveObj = redisTemplate.opsForValue().get(LAST_ACTIVE_KEY_PREFIX + userIdStr);
            if (lastActiveObj != null) {
                try {
                    lastActiveAt = LocalDateTime.parse(lastActiveObj.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[UC-CH06][Presence] Redis unavailable when getting presence for user {}: {}", userIdStr, e.getMessage());
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

        List<UUID> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();

        if (distinctIds.isEmpty()) {
            return result;
        }

        try {
            List<String> onlineKeys = distinctIds.stream().map(id -> ONLINE_KEY_PREFIX + id).toList();
            List<String> lastActiveKeys = distinctIds.stream().map(id -> LAST_ACTIVE_KEY_PREFIX + id).toList();

            List<Object> onlineVals = redisTemplate.opsForValue().multiGet(onlineKeys);
            List<Object> lastActiveVals = redisTemplate.opsForValue().multiGet(lastActiveKeys);

            for (int i = 0; i < distinctIds.size(); i++) {
                UUID uid = distinctIds.get(i);
                Object onlineObj = (onlineVals != null && i < onlineVals.size()) ? onlineVals.get(i) : null;
                Object lastActiveObj = (lastActiveVals != null && i < lastActiveVals.size()) ? lastActiveVals.get(i) : null;

                boolean isOnline = onlineObj != null
                        && ("true".equalsIgnoreCase(onlineObj.toString()) || Boolean.TRUE.equals(onlineObj));
                LocalDateTime lastActiveAt = null;
                if (lastActiveObj != null) {
                    try {
                        lastActiveAt = LocalDateTime.parse(lastActiveObj.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (Exception ignored) {}
                }

                result.put(uid, UserPresenceResponse.builder()
                        .userId(uid)
                        .online(isOnline)
                        .lastActiveAt(lastActiveAt)
                        .build());
            }
            return result;
        } catch (Exception e) {
            for (UUID uid : distinctIds) {
                result.put(uid, getPresence(uid));
            }
            return result;
        }
    }
}
