package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.AuditLogResponse;
import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetAuditLogsQuery {
    private final SettingsRepository settingsRepository;
    private final ModerationLogRepository moderationLogRepository;

    public List<AuditLogResponse> execute() {
        List<AuditLogResponse> allLogs = new ArrayList<>();

        // 1. Fetch logs from admin-service DB
        try {
            List<AuditLog> adminLogs = settingsRepository.findAllAuditLogs();
            if (adminLogs != null) {
                for (AuditLog l : adminLogs) {
                    allLogs.add(AuditLogResponse.builder()
                            .id(l.getId())
                            .adminId(l.getAdminId())
                            .adminUsername(l.getAdminUsername() != null ? l.getAdminUsername() : "admin")
                            .action(l.getAction())
                            .targetType(l.getTargetType() != null ? l.getTargetType() : "SYSTEM")
                            .targetId(l.getTargetId())
                            .details(l.getDetails())
                            .ipAddress(l.getIpAddress() != null ? l.getIpAddress() : "127.0.0.1")
                            .createdAt(l.getCreatedAt())
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch admin audit logs: {}", e.getMessage());
        }

        // 2. Fetch moderation logs directly from DB
        try {
            Page<ModerationLog> modLogs = moderationLogRepository.findAll(
                    PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            for (ModerationLog m : modLogs.getContent()) {
                String action = m.getAction() != null ? m.getAction().name() : "MODERATE_POST";
                String reason = m.getReason() != null ? m.getReason() : "";
                String note = m.getNote() != null ? m.getNote() : "";
                String details = (reason + (note.isEmpty() ? "" : " - " + note)).trim();

                allLogs.add(AuditLogResponse.builder()
                        .id(m.getId())
                        .adminId(m.getModeratorId() != null ? m.getModeratorId().toString() : "moderator")
                        .adminUsername("moderator")
                        .action(action)
                        .targetType(m.getTargetType() != null ? m.getTargetType().name() : "POST")
                        .targetId(m.getTargetId() != null ? m.getTargetId().toString() : "")
                        .details(details.isEmpty() ? "Kiểm duyệt nội dung" : details)
                        .ipAddress("127.0.0.1")
                        .createdAt(m.getCreatedAt())
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch moderation logs from repository: {}", e.getMessage());
        }

        // Sort by createdAt descending
        allLogs.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return allLogs;
    }
}
