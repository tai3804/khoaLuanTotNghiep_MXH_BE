package iuh.fit.postservice.infrastructure.client.ai;

import iuh.fit.commonframework.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ai-service", url = "${AI_SERVICE_URL:}")
public interface AiServiceClient {

    @PostMapping("/api/v1/ai/embeddings/generate")
    ApiResponse<AiEmbeddingDto> generateEmbedding(@RequestBody AiEmbeddingRequestDto request);

    @PostMapping("/api/v1/ai/embeddings/batch")
    ApiResponse<AiBatchEmbeddingDto> generateBatchEmbeddings(@RequestBody AiBatchEmbeddingRequestDto request);

    record AiEmbeddingRequestDto(String text) {}
    record AiEmbeddingDto(List<Float> embedding, int dimensions) {}
    record AiBatchEmbeddingRequestDto(List<String> texts) {}
    record AiBatchEmbeddingDto(List<List<Float>> embeddings, int dimensions, int count) {}
}

