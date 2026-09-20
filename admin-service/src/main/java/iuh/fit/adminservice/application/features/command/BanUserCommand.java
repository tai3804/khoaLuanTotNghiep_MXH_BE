package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.infrastructure.feign.AuthFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BanUserCommand {
    private final AuthFeignClient authFeignClient;

    public void execute(String userId) {
        authFeignClient.banUser(UUID.fromString(userId));
    }
    
    public void unban(String userId) {
        authFeignClient.unbanUser(UUID.fromString(userId));
    }
}
