package iuh.fit.postservice.infrastructure.client.user;

import iuh.fit.commonframework.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "user-service", configuration = UserConnectionFeignConfig.class)
public interface UserConnectionClient {
    @GetMapping("/api/v1/users/connections/status/{targetId}")
    ApiResponse<Map<String, Object>> getConnectionStatus(@PathVariable UUID targetId);

    @GetMapping("/api/v1/users/connections/friends")
    ApiResponse<Map<String, Object>> getFriends(@RequestParam int page, @RequestParam int size);
}
