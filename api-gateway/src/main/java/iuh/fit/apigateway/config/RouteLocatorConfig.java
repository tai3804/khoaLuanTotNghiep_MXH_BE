package iuh.fit.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Configuration
public class RouteLocatorConfig {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String CORS_DEDUPE_HEADERS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials";
    private static final String CORS_DEDUPE_STRATEGY = "RETAIN_UNIQUE";

    @Value("${AUTH_SERVICE_URL:lb://auth-service}")
    private String authServiceUrl;

    @Value("${USER_SERVICE_URL:lb://user-service}")
    private String userServiceUrl;

    @Value("${ADMIN_SERVICE_URL:lb://admin-service}")
    private String adminServiceUrl;

    @Value("${POST_SERVICE_URL:lb://post-service}")
    private String postServiceUrl;

    @Value("${FEED_SERVICE_URL:lb://feed-service}")
    private String feedServiceUrl;

    @Value("${NOTIFICATION_SERVICE_URL:lb://notification-service}")
    private String notificationServiceUrl;

    @Value("${MEDIA_SERVICE_URL:lb://media-service}")
    private String mediaServiceUrl;

    @Value("${CHAT_SERVICE_URL:lb://chat-service}")
    private String chatServiceUrl;

    @Value("${CALL_SERVICE_URL:lb://call-service}")
    private String callServiceUrl;

    @Value("${AI_SERVICE_URL:lb://ai-service}")
    private String aiServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/v1/auth/**", "/api/v1/devices/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(authServiceUrl, "lb://auth-service")))
                .route("user-service", r -> r.path("/api/v1/users/**", "/api/v1/groups/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(userServiceUrl, "lb://user-service")))
                .route("admin-service", r -> r.path("/api/v1/admin/**", "/api/v1/moderation/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(adminServiceUrl, "lb://admin-service")))
                .route("post-service", r -> r.path("/api/v1/posts/**", "/api/v1/stories/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(postServiceUrl, "lb://post-service")))
                .route("feed-service", r -> r.path("/api/v1/feeds/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(feedServiceUrl, "lb://feed-service")))
                .route("notification-service",
                        r -> r.path("/api/v1/notifications/**", "/ws-notifications", "/ws-notifications/**")
                                .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                                .uri(resolveUri(notificationServiceUrl, "lb://notification-service")))
                .route("media-service", r -> r.path("/api/v1/media/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(mediaServiceUrl, "lb://media-service")))
                .route("chat-service", r -> r.path("/api/v1/chat/**", "/ws-chat", "/ws-chat/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(chatServiceUrl, "lb://chat-service")))
                .route("call-service", r -> r.path("/api/v1/calls/**", "/api/v1/call/**", "/ws-call", "/ws-call/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(callServiceUrl, "lb://call-service")))
                .route("ai-service", r -> r.path("/api/v1/ai/**")
                        .filters(f -> f.dedupeResponseHeader(CORS_DEDUPE_HEADERS, CORS_DEDUPE_STRATEGY))
                        .uri(resolveUri(aiServiceUrl, "lb://ai-service")))
                .build();
    }

    /**
     * Deduplicate CORS headers on all responses so that clients (e.g. SockJS handshakes on /ws-chat/info)
     * never receive duplicate 'Access-Control-Allow-Origin' or 'Access-Control-Allow-Credentials' headers.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public GlobalFilter corsDedupeGlobalFilter(DedupeResponseHeaderGatewayFilterFactory dedupeFactory) {
        DedupeResponseHeaderGatewayFilterFactory.Config config = new DedupeResponseHeaderGatewayFilterFactory.Config();
        config.setName(CORS_DEDUPE_HEADERS);
        config.setStrategy(DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE);
        GatewayFilter filter = dedupeFactory.apply(config);
        return (exchange, chain) -> filter.filter(exchange, chain);
    }

    /**
     * Chặn tuyệt đối các đường dẫn nội bộ /api/v1/internal/** từ client bên ngoài
     * và xoá header X-Internal-Key giả mạo trên mọi request đi qua Gateway.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter internalBoundaryProtectionFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getRawPath();
            if (path != null && (path.startsWith("/api/v1/internal/") || path.contains("/api/v1/internal/"))) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                byte[] body = "{\"code\":403,\"message\":\"Truy cập đường dẫn nội bộ bị từ chối\"}"
                        .getBytes(StandardCharsets.UTF_8);
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            }

            ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.remove(INTERNAL_KEY_HEADER))
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        };
    }

    private String resolveUri(String configured, String defaultLbUri) {
        if (configured == null || configured.isBlank()) {
            return defaultLbUri;
        }
        return configured.trim();
    }
}
