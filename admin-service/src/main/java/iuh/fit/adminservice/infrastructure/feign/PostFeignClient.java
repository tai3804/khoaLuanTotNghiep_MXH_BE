package iuh.fit.adminservice.infrastructure.feign;

import iuh.fit.adminservice.application.dto.response.PostPagedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import iuh.fit.adminservice.infrastructure.config.FeignClientConfig;

@FeignClient(name = "post-service", configuration = FeignClientConfig.class)
public interface PostFeignClient {

    /**
     * Dùng public endpoint để lấy tổng số bài viết qua totalElements.
     * Endpoint /admin/count chưa được deploy vào JAR hiện tại.
     */
    @GetMapping("/api/v1/posts")
    PostPagedResponse getPosts(@RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "size", defaultValue = "1") int size);

    @GetMapping("/api/v1/posts/{postId}")
    Object getPostById(@PathVariable("postId") String postId);

    @DeleteMapping("/api/v1/posts/admin/{postId}")
    void deletePost(@PathVariable("postId") String postId);
}
