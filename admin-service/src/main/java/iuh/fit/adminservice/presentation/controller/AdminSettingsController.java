package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.application.features.command.AddBlacklistWordCommand;
import iuh.fit.adminservice.application.features.command.RemoveBlacklistWordCommand;
import iuh.fit.adminservice.application.features.query.GetAuditLogsQuery;
import iuh.fit.adminservice.application.features.query.GetBlacklistQuery;
import iuh.fit.adminservice.application.features.query.GetSystemHealthQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AddBlacklistWordCommand addBlacklistWordCommand;
    private final RemoveBlacklistWordCommand removeBlacklistWordCommand;
    private final GetBlacklistQuery getBlacklistQuery;
    private final GetAuditLogsQuery getAuditLogsQuery;
    private final GetSystemHealthQuery getSystemHealthQuery;

    // Blacklist
    @GetMapping("/blacklist")
    public ResponseEntity<List<BlacklistedWord>> getBlacklist() {
        return ResponseEntity.ok(getBlacklistQuery.execute());
    }

    @PostMapping("/blacklist")
    public ResponseEntity<BlacklistedWord> addBlacklistWord(@RequestBody String word) {
        return ResponseEntity.ok(addBlacklistWordCommand.execute(word));
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<Void> removeBlacklistWord(@PathVariable Long id) {
        removeBlacklistWordCommand.execute(id);
        return ResponseEntity.ok().build();
    }

    // Audit Logs
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(getAuditLogsQuery.execute());
    }

    // System Health
    @GetMapping("/service-health")
    public ResponseEntity<List<iuh.fit.adminservice.application.dto.response.ServiceHealthResponse>> getServiceHealth() {
        return ResponseEntity.ok(getSystemHealthQuery.execute());
    }
}
