package iuh.fit.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bổ sung các Header bảo mật chuẩn công nghiệp (OWASP Recommended Security Headers)
 * vào toàn bộ phản hồi HTTP trả về từ API Gateway.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            if (headers.getFirst("X-Content-Type-Options") == null) {
                headers.set("X-Content-Type-Options", "nosniff");
            }
            if (headers.getFirst("X-Frame-Options") == null) {
                headers.set("X-Frame-Options", "SAMEORIGIN");
            }
            if (headers.getFirst("X-XSS-Protection") == null) {
                headers.set("X-XSS-Protection", "1; mode=block");
            }
            if (headers.getFirst("Referrer-Policy") == null) {
                headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            }
            if (headers.getFirst("Permissions-Policy") == null) {
                headers.set("Permissions-Policy", "camera=(self), microphone=(self), geolocation=()");
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
