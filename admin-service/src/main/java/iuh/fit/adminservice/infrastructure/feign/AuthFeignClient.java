package iuh.fit.adminservice.infrastructure.feign;

import iuh.fit.adminservice.application.dto.response.UserInfoResponse;
import iuh.fit.adminservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "auth-service", configuration = FeignClientConfig.class)
public interface AuthFeignClient {

    @PostMapping("/api/v1/internal/users/info")
    List<UserInfoResponse> getUsersInfo(@RequestBody List<UUID> userIds);
    
    @PutMapping("/api/v1/internal/users/{userId}/ban")
    void banUser(@PathVariable("userId") UUID userId);
    
    @PutMapping("/api/v1/internal/users/{userId}/unban")
    void unbanUser(@PathVariable("userId") UUID userId);
}
