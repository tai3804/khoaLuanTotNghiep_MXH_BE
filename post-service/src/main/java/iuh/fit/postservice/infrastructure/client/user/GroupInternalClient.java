package iuh.fit.postservice.infrastructure.client.user;

import iuh.fit.commonframework.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** API nội bộ của user-service về nhóm (cần X-Internal-Key): lấy danh sách người cần nhận thông báo của nhóm. */
@FeignClient(
        name = "user-service",
        contextId = "groupInternalClient",
        url = "${USER_SERVICE_URL:}",
        configuration = ReputationClientConfig.class)
public interface GroupInternalClient {

    @GetMapping("/api/v1/internal/groups/{groupId}/notify-context")
    ApiResponse<NotifyContext> getNotifyContext(@PathVariable("groupId") UUID groupId,
                                                @RequestParam(value = "actorId", required = false) UUID actorId);

    record NotifyContext(UUID groupId, String name, List<UUID> staffIds, List<UUID> subscriberIds, String actorName, String actorAvatar) { }
}
