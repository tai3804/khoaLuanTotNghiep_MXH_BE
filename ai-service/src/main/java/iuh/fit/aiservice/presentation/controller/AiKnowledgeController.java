package iuh.fit.aiservice.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.aiservice.application.service.knowledge.KnowledgeIngestionService;
import iuh.fit.aiservice.application.service.knowledge.KnowledgeSearchService;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Quản lý kho tri thức (vector store của tài liệu hướng dẫn). Chỉ quản trị viên: mỗi lần tạo lại vector tốn lượt gọi Gemini. */
@RestController
@RequestMapping("/api/v1/ai/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI Knowledge", description = "Kho tri thức (tài liệu hướng dẫn) cho trợ lý AI: nạp vector, xem trạng thái, thử tìm kiếm")
public class AiKnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeSearchService searchService;

    @PostMapping("/reindex")
    @Operation(summary = "Nạp lại tài liệu vào vector store",
            description = "Chỉ tạo lại vector cho đoạn mới hoặc đã đổi nội dung. force=true tạo lại toàn bộ (ví dụ sau khi đổi model embedding).")
    public ResponseEntity<ApiResponse<KnowledgeIngestionService.Report>> reindex(@RequestParam(value = "force", defaultValue = "false") boolean force) {
        return ResponseEntity.ok(ApiResponse.success(ingestionService.ingest(force), "Đã nạp tài liệu"));
    }

    @GetMapping("/status")
    @Operation(summary = "Số đoạn đã có vector theo từng tài liệu")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> status() {
        return ResponseEntity.ok(ApiResponse.success(ingestionService.status(), "Trạng thái kho tri thức"));
    }

    @GetMapping("/search")
    @Operation(summary = "Thử tìm kiếm trong kho tri thức", description = "Trả về các đoạn gần nhất kèm độ giống cosine; minSimilarity=0 để xem cả đoạn dưới ngưỡng.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> search(
            @RequestParam("q") String q,
            @RequestParam(value = "k", defaultValue = "5") int k,
            @RequestParam(value = "minSimilarity", defaultValue = "0") double minSimilarity) {
        List<Map<String, Object>> rows = searchService.search(q, Math.max(1, Math.min(k, 20)), Set.of("user", "developer", "moderator", "admin"), minSimilarity)
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("similarity", Math.round(h.similarity() * 1000.0) / 1000.0);
                    m.put("doc", h.docSlug());
                    m.put("heading", h.heading());
                    m.put("preview", h.content().length() > 200 ? h.content().substring(0, 200) + "..." : h.content());
                    return m;
                }).toList();
        return ResponseEntity.ok(ApiResponse.success(rows, "Kết quả tìm kiếm kho tri thức"));
    }
}
