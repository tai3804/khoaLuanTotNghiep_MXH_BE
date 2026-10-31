package iuh.fit.feedservice.infrastructure.client;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.feedservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", url = "${USER_SERVICE_URL:}", configuration = FeignClientConfig.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/users/{userId}/audience-ids")
    ApiResponse<List<UUID>> getAudienceIds(@PathVariable("userId") UUID userId);
}
