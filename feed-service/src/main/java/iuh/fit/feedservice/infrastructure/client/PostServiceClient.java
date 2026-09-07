package iuh.fit.feedservice.infrastructure.client;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.feedservice.infrastructure.client.dto.PostResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "post-service", path = "/api/v1/posts")
public interface PostServiceClient {

    @GetMapping("/{postId}")
    ApiResponse<PostResponseDto> getPostById(@PathVariable("postId") UUID postId);
}
