package iuh.fit.aiservice.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.aiservice.application.dto.AiBatchEmbeddingRequest;
import iuh.fit.aiservice.application.dto.AiBatchEmbeddingResponse;
import iuh.fit.aiservice.application.dto.AiEmbeddingRequest;
import iuh.fit.aiservice.application.dto.AiEmbeddingResponse;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/embeddings")
@RequiredArgsConstructor
@Tag(name = "AI Embedding", description = "Dịch vụ tạo vector embedding 768 chiều phục vụ tìm kiếm ngữ nghĩa (Semantic Search)")
public class AiEmbeddingController {

    private final GeminiApiClient geminiApiClient;

    @PostMapping("/generate")
    @Operation(summary = "Tạo vector embedding 768 chiều từ văn bản", description = "Sử dụng Google Gemini Embedding với outputDimensionality=768")
    public ResponseEntity<ApiResponse<AiEmbeddingResponse>> generateEmbedding(@RequestBody AiEmbeddingRequest request) {
        String text = request != null && request.getText() != null ? request.getText().trim() : "";
        List<Float> vector = geminiApiClient.generateEmbedding(text);
        
        AiEmbeddingResponse response = AiEmbeddingResponse.builder()
                .embedding(vector)
                .dimensions(vector.size())
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(response, "Tạo vector embedding thành công"));
    }

    @PostMapping("/batch")
    @Operation(summary = "Tạo vector embedding hàng loạt", description = "Tạo vector embedding 768 chiều cho danh sách văn bản")
    public ResponseEntity<ApiResponse<AiBatchEmbeddingResponse>> generateBatchEmbeddings(@RequestBody AiBatchEmbeddingRequest request) {
        List<String> texts = request != null && request.getTexts() != null ? request.getTexts() : List.of();
        List<List<Float>> vectors = geminiApiClient.generateBatchEmbeddings(texts);

        int dims = vectors.isEmpty() ? 768 : (vectors.get(0).isEmpty() ? 0 : vectors.get(0).size());

        AiBatchEmbeddingResponse response = AiBatchEmbeddingResponse.builder()
                .embeddings(vectors)
                .dimensions(dims)
                .count(vectors.size())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Tạo vector embedding hàng loạt thành công"));
    }
}

