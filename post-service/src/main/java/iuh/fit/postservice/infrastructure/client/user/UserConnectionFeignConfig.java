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
            String authorization = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null && attributes.getRequest() != null) {
                authorization = attributes.getRequest().getHeader("Authorization");
            }
            if (authorization == null || authorization.isBlank()) {
                var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                    if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                        authorization = "Bearer " + jwt.getTokenValue();
                    } else if (auth.getCredentials() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                        authorization = "Bearer " + jwt.getTokenValue();
                    } else if (auth.getCredentials() instanceof String creds && !creds.isBlank()) {
                        authorization = creds.startsWith("Bearer ") ? creds : "Bearer " + creds;
                    }
                }
            }
            if (authorization != null && !authorization.isBlank()) {
                template.header("Authorization", authorization);
            }
        };
    }
}
