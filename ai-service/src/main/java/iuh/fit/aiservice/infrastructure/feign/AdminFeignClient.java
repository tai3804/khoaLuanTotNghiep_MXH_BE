package iuh.fit.aiservice.infrastructure.feign;

import iuh.fit.aiservice.infrastructure.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "admin-service", url = "${ADMIN_SERVICE_URL:}", contextId = "adminServiceClient", configuration = FeignClientConfig.class)
public interface AdminFeignClient {

    @PostMapping("/api/v1/internal/admin/blacklist/learn")
    void learnSensitiveWord(@RequestBody String word);

    /** Từ cấm do quản trị viên thêm ở trang "Từ điển từ cấm". */
    @GetMapping("/api/v1/internal/admin/blacklist")
    List<String> getBlacklistedWords();

    @GetMapping("/api/v1/internal/admin/system-configs")
    Map<String, String> getSystemConfigs();
}
