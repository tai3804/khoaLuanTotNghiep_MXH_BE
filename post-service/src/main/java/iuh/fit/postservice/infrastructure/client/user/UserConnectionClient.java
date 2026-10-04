package iuh.fit.postservice.infrastructure.client.user;

import iuh.fit.commonframework.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@FeignClient(name = "user-service", url = "${USER_SERVICE_URL:http://localhost:8085}", configuration = UserConnectionFeignConfig.class)
public interface UserConnectionClient {
    @GetMapping("/api/v1/users/connections/status/{targetId}")
    ApiResponse<Map<String, Object>> getConnectionStatus(@PathVariable("targetId") UUID targetId);

    @GetMapping("/api/v1/users/connections/friends")
    ApiResponse<Map<String, Object>> getFriends(@RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/api/v1/groups/feed-visibility")
    ApiResponse<List<GroupFeedVisibility>> getGroupFeedVisibility(@RequestBody List<UUID> groupIds);

    record GroupFeedVisibility(UUID groupId, boolean member, boolean publicGroup, String role, boolean postApprovalRequired) { }
}
