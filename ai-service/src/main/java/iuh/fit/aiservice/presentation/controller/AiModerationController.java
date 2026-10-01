package iuh.fit.aiservice.presentation.controller;

import iuh.fit.aiservice.application.service.AiModerationService;
import iuh.fit.aiservice.domain.entities.AiModerationLog;
import iuh.fit.aiservice.domain.repository.AiModerationLogRepository;
import iuh.fit.aiservice.domain.repository.SensitiveKeywordRepository;
import iuh.fit.aiservice.infrastructure.gemini.GeminiModerationResult;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/moderation")
@RequiredArgsConstructor
public class AiModerationController {

    private final AiModerationService aiModerationService;
    private final AiModerationLogRepository logRepository;
    private final SensitiveKeywordRepository keywordRepository;

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<GeminiModerationResult>> evaluateText(@RequestBody EvaluateRequest request) {
        GeminiModerationResult result = aiModerationService.evaluateTextContent(request.getContent());
        return ResponseEntity.ok(ApiResponse.success(result, "Đánh giá văn bản bằng AI thành công"));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<AiModerationLog>>> getModerationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AiModerationLog> logsPage = logRepository.findByOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ResponseEntity.ok(ApiResponse.paged(logsPage.getContent(), logsPage, "Lấy danh sách nhật ký kiểm duyệt AI thành công"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAiStats() {
        long totalLogs = logRepository.count();
        long totalFlagged = logRepository.countFlaggedContent();
        long totalKeywords = keywordRepository.count();
        long autoLearnedKeywords = keywordRepository.countByIsAutoLearnedTrue();

        Map<String, Object> stats = Map.of(
                "totalEvaluations", totalLogs,
                "totalFlagged", totalFlagged,
                "totalKeywords", totalKeywords,
                "autoLearnedKeywords", autoLearnedKeywords
        );

        return ResponseEntity.ok(ApiResponse.success(stats, "Lấy thống kê AI kiểm duyệt thành công"));
    }

    @Data
    public static class EvaluateRequest {
        private String content;
    }
}
