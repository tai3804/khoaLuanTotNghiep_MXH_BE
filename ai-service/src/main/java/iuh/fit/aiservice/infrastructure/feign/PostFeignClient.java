package iuh.fit.aiservice.infrastructure.feign;

import iuh.fit.aiservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.aiservice.application.dto.PostResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "post-service", url = "${POST_SERVICE_URL:}", contextId = "postServiceClient", configuration = FeignClientConfig.class)
public interface PostFeignClient {

    @GetMapping("/api/v1/posts/{id}")
    ApiResponse<PostResponse> getPost(@PathVariable("id") UUID id);

    /** Tìm kiếm ngữ nghĩa (vector + đồ thị xã hội + hashtag); chỉ trả về bài người gọi được phép xem. */
    @GetMapping("/api/v1/posts/search")
    ApiResponse<List<PostResponse>> searchPosts(@RequestParam("query") String query,
                                                @RequestParam("size") int size);

    @PostMapping("/api/v1/posts/{id}/embedding")
    ApiResponse<Boolean> savePostEmbedding(@PathVariable("id") UUID id, @RequestBody List<Float> embedding);
}
