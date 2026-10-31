package iuh.fit.adminservice.application.features.user.commands.send_notification;

import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendNotificationCommandHandler {

    AuditLogService auditLogService;
    KafkaTemplate<String, Object> kafkaTemplate;
    UserFeignClient userFeignClient;

    public void handle(Map<String, Object> payload) {
        String recipientId = (String) payload.get("recipientId");
        String title = (String) payload.get("title");
        String content = (String) payload.get("content");
        String type = (String) payload.get("type");
        String targetUrl = (String) payload.get("targetUrl");

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            log.warn("Cannot send admin notification: title or content is blank");
            return;
        }

        String notifType = (type != null && !type.isBlank()) ? type : "SYSTEM";
        log.info("Admin sending notification: recipientId={}, title={}, type={}", recipientId, title, notifType);

        if (recipientId != null && !recipientId.isBlank() && !"ALL".equalsIgnoreCase(recipientId) && !"BROADCAST".equalsIgnoreCase(recipientId)) {
            sendToUser(recipientId.trim(), title, content, notifType, targetUrl);
        } else {
            // Broadcast to all active users
            try {
                List<UserResponse> users = userFeignClient.getAllUsers();
                if (users != null) {
                    for (UserResponse user : users) {
                        String id = user.getUserId() != null ? user.getUserId() : user.getId();
                        if (id != null && !id.isBlank()) {
                            sendToUser(id, title, content, notifType, targetUrl);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to broadcast admin notification to all users: {}", e.getMessage());
            }
        }

        auditLogService.log("SEND_NOTIFICATION", "USER", recipientId != null ? recipientId : "BROADCAST", "Gửi thông báo: " + title);
    }

    private void sendToUser(String userId, String title, String content, String type, String targetUrl) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("recipientId", userId);
            event.put("actorId", null);
            event.put("type", type);
            event.put("title", title);
            event.put("content", content);
            event.put("targetId", "");
            event.put("targetUrl", targetUrl != null ? targetUrl : "");
            event.put("avatarUrl", "");

            kafkaTemplate.send("notification.in-app.send", event);
        } catch (Exception e) {
            log.error("Failed to send Kafka in-app notification event to user {}: {}", userId, e.getMessage());
        }
    }
}
