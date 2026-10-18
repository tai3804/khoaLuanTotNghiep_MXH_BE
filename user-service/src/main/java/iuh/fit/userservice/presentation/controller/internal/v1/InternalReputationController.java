package iuh.fit.userservice.presentation.controller.internal.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.userservice.application.service.ReputationPolicy;
import iuh.fit.userservice.application.service.ReputationService;
import iuh.fit.userservice.presentation.dto.response.ReputationResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API nội bộ về điểm uy tín cho các service khác (cần header X-Internal-Key, không đi qua gateway):
 * post-service hỏi quyền đăng bài/bình luận và độ ưu tiên bảng tin của tác giả.
 */
@RestController
@RequestMapping("/api/v1/internal/reputation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal Reputation", description = "Internal APIs on reputation policy, used by other services")
public class InternalReputationController {

    ReputationService reputationService;

    public record PolicyView(int score, ReputationPolicy policy) { }

    public record FeedWeightRequest(List<UUID> userIds) { }

    @GetMapping("/{userId}/policy")
    @Operation(summary = "Current reputation tier and its restrictions for a user")
    public ResponseEntity<ApiResponse<PolicyView>> getPolicy(@PathVariable UUID userId) {
        ReputationResponse reputation = reputationService.getReputation(userId);
        return ResponseEntity.ok(ApiResponse.success(
                new PolicyView(reputation.getScore(), reputation.getPolicy()), "Reputation policy retrieved successfully"));
    }

    @PostMapping("/feed-weights")
    @Operation(summary = "Feed ranking multipliers for authors whose reputation lowers their reach (others default to 1)")
    public ResponseEntity<ApiResponse<Map<UUID, Double>>> getFeedWeights(@RequestBody FeedWeightRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                reputationService.getReducedFeedWeights(request != null ? request.userIds() : List.of()),
                "Feed weights retrieved successfully"));
    }

    @PostMapping("/maintenance")
    @Operation(summary = "Run the reputation maintenance now: daily recovery and unlock of expired locks")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runMaintenance() {
        return ResponseEntity.ok(ApiResponse.success(reputationService.runMaintenance(), "Reputation maintenance finished"));
    }
}
