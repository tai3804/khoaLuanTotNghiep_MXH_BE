package iuh.fit.adminservice.infrastructure.feign;

import iuh.fit.adminservice.application.dto.response.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

import iuh.fit.adminservice.infrastructure.config.FeignClientConfig;

@FeignClient(name = "user-service", configuration = FeignClientConfig.class)
public interface UserFeignClient {
    
    @GetMapping("/api/v1/users/admin/all")
    List<UserResponse> getAllUsers();

    @GetMapping("/api/v1/users/admin/count")
    Long countUsers();

    @PutMapping("/api/v1/users/admin/{userId}/ban")
    void banUser(@PathVariable("userId") String userId);
    
    @PutMapping("/api/v1/users/admin/{userId}/unban")
    void unbanUser(@PathVariable("userId") String userId);

    @GetMapping("/api/v1/groups/admin/all")
    List<Object> getAllGroups();

    @DeleteMapping("/api/v1/groups/admin/{groupId}")
    void deleteGroup(@PathVariable("groupId") String groupId);
}

