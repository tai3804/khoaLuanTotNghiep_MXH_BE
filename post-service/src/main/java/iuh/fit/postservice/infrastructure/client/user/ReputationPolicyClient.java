package iuh.fit.postservice.infrastructure.client.user;

import iuh.fit.commonframework.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chính sách quyền hạn theo bậc uy tín do user-service quyết định (API nội bộ, cần X-Internal-Key). */
@FeignClient(
        name = "user-service",
        contextId = "reputationPolicyClient",
        url = "${USER_SERVICE_URL:http://localhost:8085}",
        configuration = ReputationClientConfig.class)
public interface ReputationPolicyClient {

    @GetMapping("/api/v1/internal/reputation/{userId}/policy")
    ApiResponse<PolicyView> getPolicy(@PathVariable("userId") UUID userId);

    /** Hệ số xếp hạng bảng tin của các tác giả bị giảm độ ưu tiên; tác giả không có trong kết quả = 1. */
    @PostMapping("/api/v1/internal/reputation/feed-weights")
    ApiResponse<Map<UUID, Double>> getFeedWeights(@RequestBody FeedWeightRequest request);

    record FeedWeightRequest(List<UUID> userIds) { }

    /** Phần quyền hạn mà post-service cần (các trường khác của user-service được bỏ qua). */
    record PolicyView(int score, Policy policy) { }

    record Policy(
            String level,
            String levelLabel,
            int minPostIntervalSeconds,
            int maxPostsPerDay,
            int minCommentIntervalSeconds,
            int maxCommentsPerDay,
            boolean canPost,
            boolean canComment,
            boolean canGoLive,
            boolean accountLocked) { }
}
