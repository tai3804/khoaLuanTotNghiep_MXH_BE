package iuh.fit.feedservice.infrastructure.client;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import lombok.Builder;
import lombok.Data;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/users/connections")
public interface UserServiceClient {

    @GetMapping("/followers")
    ApiResponse<PagedResponse<UserConnectionDto>> getFollowers(
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @Data
    @Builder
    class UserConnectionDto {
        UUID id;
        UUID userId;
        UUID connectedUserId;
        String status;
    }
}
