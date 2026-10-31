package iuh.fit.aiservice.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import iuh.fit.commonframework.infrastructure.security.InternalApiKeyFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Configuration
public class FeignClientConfig {

    /** Các API nội bộ /api/v1/internal/** của service khác yêu cầu khoá dịch vụ (kể cả khi gọi từ luồng Kafka không có JWT). */
    @Value("${app.security.internal-api-key:kltn-internal-dev-key}")
    private String internalApiKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                if (template.url().startsWith("/api/v1/internal/")) {
                    template.header(InternalApiKeyFilter.HEADER, internalApiKey);
                }
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String authorization = request.getHeader("Authorization");
                    if (authorization != null && !authorization.isBlank()) {
                        template.header("Authorization", authorization);
                        log.debug("Forwarded Authorization header to Feign call: {}", template.url());
                    }
                } else {
                    // Running in background Kafka thread, provide internal key for service calls
                    template.header(InternalApiKeyFilter.HEADER, internalApiKey);
                }
            }
        };
    }
}
