package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.request.ProcessReportRequest;
import iuh.fit.adminservice.application.features.command.AddBlacklistWordCommand;
import iuh.fit.adminservice.application.features.query.GetSystemConfigsQuery;
import iuh.fit.adminservice.application.features.report.commands.process_report.ProcessReportCommand;
import iuh.fit.adminservice.application.features.report.commands.process_report.ProcessReportCommandHandler;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin")
@RequiredArgsConstructor
public class AdminInternalController {

    private final AddBlacklistWordCommand addBlacklistWordCommand;
    private final GetSystemConfigsQuery getSystemConfigsQuery;
    private final ProcessReportCommandHandler processReportCommandHandler;
    private final SettingsRepository settingsRepository;

    @PostMapping("/blacklist/learn")
    public ResponseEntity<BlacklistedWord> learnSensitiveWord(@RequestBody String word) {
        return ResponseEntity.ok(addBlacklistWordCommand.execute(word));
    }

    /** Danh sách từ cấm quản trị viên đã thêm; ai-service đọc để chặn cứng bài viết chứa các từ này. */
    @GetMapping("/blacklist")
    public ResponseEntity<List<String>> getBlacklistedWords() {
        return ResponseEntity.ok(settingsRepository.findAllBlacklistedWords().stream().map(BlacklistedWord::getWord).toList());
    }

    @GetMapping("/system-configs")
    public ResponseEntity<Map<String, String>> getInternalSystemConfigs() {
        return ResponseEntity.ok(getSystemConfigsQuery.execute());
    }

    @PostMapping("/reports/{reportId}/process")
    public ResponseEntity<Void> processReportInternal(
            @PathVariable UUID reportId,
            @RequestBody ProcessReportRequest request) {
        UUID systemModeratorId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        ProcessReportCommand command = ProcessReportCommand.builder()
                .reportId(reportId)
                .moderatorId(systemModeratorId)
                .action(request.getAction())
                .reason(request.getReason() != null ? request.getReason() : "AI_AUTO_MODERATION")
                .note(request.getNote())
                .build();
        processReportCommandHandler.handle(command);
        return ResponseEntity.ok().build();
    }
}
