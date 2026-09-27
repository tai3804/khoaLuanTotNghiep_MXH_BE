package iuh.fit.aiservice.infrastructure.feign;

import iuh.fit.aiservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.aiservice.application.dto.PostResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "post-service", configuration = FeignClientConfig.class)
public interface PostFeignClient {

    @GetMapping("/api/v1/posts/{id}")
    ApiResponse<PostResponse> getPost(@PathVariable("id") UUID id);
}
