package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.response.AuditLogResponse;
import iuh.fit.adminservice.application.dto.response.ServiceHealthResponse;
import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.application.features.command.AddBlacklistWordCommand;
import iuh.fit.adminservice.application.features.command.RemoveBlacklistWordCommand;
import iuh.fit.adminservice.application.features.command.UpdateSystemConfigsCommand;
import iuh.fit.adminservice.application.features.query.GetAuditLogsQuery;
import iuh.fit.adminservice.application.features.query.GetBlacklistQuery;
import iuh.fit.adminservice.application.features.query.GetSystemConfigsQuery;
import iuh.fit.adminservice.application.features.query.GetSystemHealthQuery;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AddBlacklistWordCommand addBlacklistWordCommand;
    private final RemoveBlacklistWordCommand removeBlacklistWordCommand;
    private final GetBlacklistQuery getBlacklistQuery;
    private final GetAuditLogsQuery getAuditLogsQuery;
    private final GetSystemHealthQuery getSystemHealthQuery;
    private final GetSystemConfigsQuery getSystemConfigsQuery;
    private final UpdateSystemConfigsCommand updateSystemConfigsCommand;
    private final AuditLogService auditLogService;
    private final RedisCacheService redisCacheService;

    // Blacklist - Available to both ADMIN and MODERATOR
    @GetMapping("/blacklist")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<List<BlacklistedWord>> getBlacklist() {
        return ResponseEntity.ok(getBlacklistQuery.execute());
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<BlacklistedWord> addBlacklistWord(@RequestBody String word) {
        BlacklistedWord saved = addBlacklistWordCommand.execute(word);
        auditLogService.log("ADD_BLACKLIST", "BLACKLIST", word != null ? word.trim() : "", "Thêm từ khóa cấm vào bộ lọc");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/blacklist/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> removeBlacklistWord(@PathVariable Long id) {
        removeBlacklistWordCommand.execute(id);
        auditLogService.log("REMOVE_BLACKLIST", "BLACKLIST", String.valueOf(id), "Gỡ từ khóa cấm khỏi bộ lọc");
        return ResponseEntity.ok().build();
    }

    // System Configs - ADMIN only
    @GetMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getSystemConfigs() {
        return ResponseEntity.ok(getSystemConfigsQuery.execute());
    }

    @PutMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> updateSystemConfigs(@RequestBody Map<String, String> configs) {
        updateSystemConfigsCommand.execute(configs);
        auditLogService.log("UPDATE_SYSTEM_CONFIG", "SYSTEM", "CONFIG", "Cập nhật tham số cấu hình hệ thống");
        return ResponseEntity.ok(getSystemConfigsQuery.execute());
    }

    // Public System Configs - accessible without authentication
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfigs() {
        Map<String, String> allConfigs = getSystemConfigsQuery.execute();
        Set<String> allowedPublicKeys = Set.of(
                "maintenance_mode",
                "allow_registration",
                "ai_moderation_enabled",
                "max_upload_size_mb",
                "system_announcement",
                "max_reports_auto_hide"
        );
        Map<String, String> publicConfigs = new HashMap<>();
        if (allConfigs != null) {
            for (Map.Entry<String, String> entry : allConfigs.entrySet()) {
                if (allowedPublicKeys.contains(entry.getKey())) {
                    publicConfigs.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return ResponseEntity.ok(publicConfigs);
    }

    // Clear Cache - ADMIN only
    @PostMapping("/clear-cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearCache(@RequestParam(defaultValue = "all") String type) {
        String cleanType = type != null ? type.trim().toLowerCase() : "all";
        try {
            switch (cleanType) {
                case "blacklist" -> redisCacheService.deletePattern("admin-service:blacklist*");
                case "posts" -> {
                    redisCacheService.deletePattern("post-service:*");
                    redisCacheService.deletePattern("feed-service:*");
                }
                case "token" -> {
                    // Do NOT delete token revocation state or token versions, as that would invalidate security!
                    log.info("Token cache synchronization checked by admin.");
                }
                case "all" -> {
                    redisCacheService.deletePattern("admin-service:*");
                    redisCacheService.deletePattern("post-service:*");
                    redisCacheService.deletePattern("feed-service:*");
                    redisCacheService.deletePattern("user-service:*");
                }
                default -> redisCacheService.deletePattern(cleanType + ":*");
            }
        } catch (Exception e) {
            log.error("Failed to clear redis cache for type {}: {}", cleanType, e.getMessage());
        }

        auditLogService.log("CLEAR_CACHE", "CACHE", cleanType.toUpperCase(), "Xóa bộ nhớ đệm hệ thống: " + cleanType);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "type", cleanType,
                "message", "Đã dọn dẹp và làm mới bộ nhớ đệm " + cleanType + " thành công"
        ));
    }

    // Audit Logs - ADMIN only
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs() {
        return ResponseEntity.ok(getAuditLogsQuery.execute());
    }

    // System Health - ADMIN only
    @GetMapping("/service-health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServiceHealthResponse>> getServiceHealth() {
        return ResponseEntity.ok(getSystemHealthQuery.execute());
    }
}
