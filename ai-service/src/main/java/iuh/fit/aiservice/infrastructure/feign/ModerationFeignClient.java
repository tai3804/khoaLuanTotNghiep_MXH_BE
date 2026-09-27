package iuh.fit.aiservice.infrastructure.feign;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.aiservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;
import lombok.Data;

@FeignClient(name = "admin-service", contextId = "moderationServiceClient", configuration = FeignClientConfig.class)
public interface ModerationFeignClient {

    @PostMapping("/api/v1/moderation/reports/{reportId}/process")
    ApiResponse<Void> processReport(@PathVariable("reportId") UUID reportId, @RequestBody ProcessReportRequest request);

    @Data
    class ProcessReportRequest {
        private String action; // e.g., DELETE_POST, DISMISS
        private String reason; // AI_AUTO_APPROVED
        private String note;
    }
}
