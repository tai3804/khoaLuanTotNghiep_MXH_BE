package iuh.fit.aiservice.presentation.controller;

import iuh.fit.aiservice.application.service.SelfLearningService;
import iuh.fit.aiservice.domain.entities.SensitiveKeyword;
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
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/keywords")
@RequiredArgsConstructor
public class AiKeywordController {

    private final SelfLearningService selfLearningService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SensitiveKeyword>>> getKeywords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<SensitiveKeyword> results = selfLearningService.searchKeywords(
                keyword,
                category,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return ResponseEntity.ok(ApiResponse.paged(results.getContent(), results, "Lấy danh sách từ khóa nhạy cảm thành công"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addKeyword(@RequestBody CreateKeywordRequest request) {
        selfLearningService.learnKeywords(
                List.of(request.getKeyword()),
                request.getCategory(),
                request.getSeverity(),
                false // Admin manual add
        );

        return ResponseEntity.ok(ApiResponse.success(null, "Thêm từ khóa nhạy cảm thành công"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SensitiveKeyword>> approveKeyword(@PathVariable UUID id) {
        SensitiveKeyword approved = selfLearningService.approveAutoLearnedKeyword(id);
        return ResponseEntity.ok(ApiResponse.success(approved, "Duyệt từ khóa do AI tự học thành công"));
    }

    @PostMapping("/{id}/whitelist")
    public ResponseEntity<ApiResponse<Void>> whitelistKeyword(@PathVariable UUID id) {
        selfLearningService.whitelistKeyword(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đưa từ khóa vào danh sách an toàn (Whitelist)"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKeyword(@PathVariable UUID id) {
        selfLearningService.deleteKeyword(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa từ khóa thành công"));
    }

    @Data
    public static class CreateKeywordRequest {
        private String keyword;
        private String category; // PROFANITY, HATE_SPEECH, HARASSMENT, SEXUAL, VIOLENCE, SPAM
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    }
}
