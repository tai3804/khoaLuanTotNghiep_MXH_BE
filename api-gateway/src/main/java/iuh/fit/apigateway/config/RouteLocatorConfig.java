package iuh.fit.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/v1/auth/**", "/api/v1/devices/**")
                        .uri("lb://auth-service"))
                .route("user-service", r -> r.path("/api/v1/users/**")
                        .uri("lb://user-service"))
                .route("post-service", r -> r.path("/api/v1/posts/**")
                        .uri("lb://post-service"))
                .route("feed-service", r -> r.path("/api/v1/feeds/**")
                        .uri("lb://feed-service"))
                .route("notification-service", r -> r.path("/api/v1/notifications/**")
                        .uri("lb://notification-service"))
                .route("media-service", r -> r.path("/api/v1/media/**")
                        .uri("lb://media-service"))
                .route("chat-service", r -> r.path("/api/v1/chat/**")
                        .uri("lb://chat-service"))
                .route("call-service", r -> r.path("/api/v1/calls/**", "/api/v1/call/**")
                        .uri("lb://call-service"))
                .build();
    }
}
