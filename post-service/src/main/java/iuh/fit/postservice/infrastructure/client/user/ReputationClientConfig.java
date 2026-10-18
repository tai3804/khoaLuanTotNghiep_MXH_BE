package iuh.fit.postservice.infrastructure.client.user;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình riêng của ReputationPolicyClient: gửi khoá dịch vụ để gọi API nội bộ. Cố ý KHÔNG đánh dấu @Configuration để
 * khoá này không bị áp sang các Feign client khác (media-service, user-service công khai...).
 */
public class ReputationClientConfig {

    @Bean
    RequestInterceptor internalApiKeyInterceptor(@Value("${app.security.internal-api-key:kltn-internal-dev-key}") String key) {
        return template -> template.header("X-Internal-Key", key);
    }
}
