package iuh.fit.adminservice.application.features.user.queries.get_all_users;

import iuh.fit.adminservice.application.dto.response.UserInfoResponse;
import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.infrastructure.feign.AuthFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetAllUsersQueryHandler {

    UserFeignClient userFeignClient;
    AuthFeignClient authFeignClient;

    public List<UserResponse> handle() {
        try {
            List<UserResponse> users = userFeignClient.getAllUsers();
            if (users != null && !users.isEmpty()) {
                try {
                    List<UUID> userIds = users.stream()
                            .map(u -> UUID.fromString(u.getUserId() != null ? u.getUserId() : u.getId()))
                            .collect(Collectors.toList());

                    List<UserInfoResponse> authInfos = authFeignClient.getUsersInfo(userIds);

                    Map<String, UserInfoResponse> infoMap = authInfos.stream()
                            .collect(Collectors.toMap(
                                    info -> info.getId().toString(),
                                    info -> info
                            ));

                    for (UserResponse u : users) {
                        String id = u.getUserId() != null ? u.getUserId() : u.getId();
                        UserInfoResponse info = infoMap.get(id);
                        if (info != null) {
                            u.setEmail(info.getEmail());
                            u.setStatus(info.getStatus() != null ? info.getStatus() : "ACTIVE");
                            if (info.getRoles() != null) {
                                if (info.getRoles().contains("ROLE_ADMIN") || info.getRoles().contains("ADMIN")) {
                                    u.setRole("ADMIN");
                                } else if (info.getRoles().contains("ROLE_MODERATOR") || info.getRoles().contains("MODERATOR")) {
                                    u.setRole("MODERATOR");
                                } else {
                                    u.setRole("USER");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch user roles from Auth service: {}", e.getMessage());
                }
            }
            log.info("Fetched {} users via UserFeignClient", users != null ? users.size() : 0);
            return users != null ? users : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch users via UserFeignClient: {}", e.getMessage());
            return List.of();
        }
    }
}
