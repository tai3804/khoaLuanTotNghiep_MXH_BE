package iuh.fit.postservice.infrastructure.client.user;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class UserConnectionFeignConfig {
    @Bean
    RequestInterceptor forwardAuthorizationHeader() {
        return template -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return;
            String authorization = attributes.getRequest().getHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) template.header("Authorization", authorization);
        };
    }
}
