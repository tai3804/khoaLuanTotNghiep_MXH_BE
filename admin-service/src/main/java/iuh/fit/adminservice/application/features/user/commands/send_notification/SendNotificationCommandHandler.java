package iuh.fit.adminservice.application.features.user.commands.send_notification;

import iuh.fit.adminservice.application.service.AuditLogService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendNotificationCommandHandler {

    AuditLogService auditLogService;

    public void handle(Map<String, Object> payload) {
        String recipientId = (String) payload.get("recipientId");
        String title = (String) payload.get("title");
        String content = (String) payload.get("content");
        String type = (String) payload.get("type");
        log.info("Admin sending notification to {}: title={}, type={}", recipientId, title, type);

        auditLogService.log("SEND_NOTIFICATION", "USER", recipientId != null ? recipientId : "BROADCAST", "Gửi thông báo: " + title);
    }
}
