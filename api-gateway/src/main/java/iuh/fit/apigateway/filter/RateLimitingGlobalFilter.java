package iuh.fit.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ lọc giới hạn tần suất yêu cầu (Rate Limiting) tại API Gateway.
 * Sử dụng thuật toán Fixed Window Counter phân cấp theo loại tài nguyên (Auth, AI, Mutation, General),
 * kết hợp cơ chế tự dọn dẹp bộ nhớ định kỳ.
 *
 * Toàn bộ tham số cấu hình được nạp động từ file .env / biến môi trường:
 * - RATE_LIMIT_ENABLED (true/false)
 * - RATE_LIMIT_AUTH_PER_MINUTE (mặc định 20)
 * - RATE_LIMIT_AI_PER_MINUTE (mặc định 30)
 * - RATE_LIMIT_MUTATION_PER_MINUTE (mặc định 60)
 * - RATE_LIMIT_GENERAL_PER_MINUTE (mặc định 180)
 * - RATE_LIMIT_RETRY_AFTER_SECONDS (mặc định 30)
 */
@Component
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingGlobalFilter.class);

    @Value("${RATE_LIMIT_ENABLED:true}")
    private boolean rateLimitEnabled;

    @Value("${RATE_LIMIT_AUTH_PER_MINUTE:20}")
    private int authLimitPerMinute;

    @Value("${RATE_LIMIT_AI_PER_MINUTE:30}")
    private int aiLimitPerMinute;

    @Value("${RATE_LIMIT_MUTATION_PER_MINUTE:60}")
    private int mutationLimitPerMinute;

    @Value("${RATE_LIMIT_GENERAL_PER_MINUTE:180}")
    private int generalLimitPerMinute;

    @Value("${RATE_LIMIT_RETRY_AFTER_SECONDS:30}")
    private int retryAfterSeconds;

    private static final long WINDOW_MS = 60_000L; // 1 phút

    private final Map<String, WindowCounter> requestCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    public RateLimitingGlobalFilter() {
        // Định kỳ mỗi 60 giây dọn dẹp các bucket đã hết hạn trên 2 phút để giải phóng bộ nhớ
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredBuckets, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public int getOrder() {
        // Chạy sau internal boundary filter và correlation ID filter
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Nếu biến RATE_LIMIT_ENABLED trong .env tắt, cho phép toàn bộ request đi qua
        if (!rateLimitEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();

        // 1. Bỏ qua các yêu cầu CORS Preflight (OPTIONS)
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getRawPath();
        if (path == null) {
            return chain.filter(exchange);
        }

        // 2. Bỏ qua WebSocket handshake/info để không làm gián đoạn trò chuyện và cuộc gọi thời gian thực
        if (isWebSocketPath(path) || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // 3. Xác định nhóm giới hạn và hạn mức
        int limit = resolveLimit(path, request.getMethod());
        String clientKey = resolveClientKey(request);
        String bucketKey = pathCategory(path, request.getMethod()) + ":" + clientKey;

        long currentWindow = System.currentTimeMillis() / WINDOW_MS;
        WindowCounter counter = requestCounts.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new WindowCounter(currentWindow, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int currentCount = counter.count.get();
        int remaining = Math.max(0, limit - currentCount);
        long resetSeconds = ((currentWindow + 1) * WINDOW_MS - System.currentTimeMillis()) / 1000L;
        long effectiveRetryAfter = Math.max(1, Math.min(resetSeconds, retryAfterSeconds));

        // 4. Nếu vượt quá hạn mức, trả về HTTP 429 Too Many Requests
        if (currentCount > limit) {
            log.warn("[RateLimit] Chặn yêu cầu quá giới hạn: key={}, path={}, count={}, limit={}",
                    bucketKey, path, currentCount, limit);

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Retry-After", String.valueOf(effectiveRetryAfter));
            headers.set("X-RateLimit-Limit", String.valueOf(limit));
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("X-RateLimit-Reset", String.valueOf(resetSeconds));

            String bodyJson = String.format(
                    "{\"code\":429,\"message\":\"Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ %d giây trước khi thử lại.\",\"retryAfter\":%d}",
                    effectiveRetryAfter, effectiveRetryAfter
            );
            byte[] bytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }

        // 5. Gắn các header RateLimit vào phản hồi để client theo dõi
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders resHeaders = exchange.getResponse().getHeaders();
            resHeaders.set("X-RateLimit-Limit", String.valueOf(limit));
            resHeaders.set("X-RateLimit-Remaining", String.valueOf(remaining));
            resHeaders.set("X-RateLimit-Reset", String.valueOf(resetSeconds));
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private boolean isWebSocketPath(String path) {
        return path.startsWith("/ws-chat") || path.startsWith("/ws-notifications") || path.startsWith("/ws-call");
    }

    private int resolveLimit(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/mfa")) {
            return authLimitPerMinute;
        }

        if (path.startsWith("/api/v1/ai")) {
            return aiLimitPerMinute;
        }

        if (isMutationMethod(method) && (path.startsWith("/api/v1/posts") || path.startsWith("/api/v1/media") || path.startsWith("/api/v1/groups"))) {
            return mutationLimitPerMinute;
        }

        return generalLimitPerMinute;
    }

    private String pathCategory(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth")) return "auth";
        if (path.startsWith("/api/v1/ai")) return "ai";
        if (isMutationMethod(method)) return "mutation";
        return "general";
    }

    private boolean isMutationMethod(HttpMethod method) {
        return HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)
                || HttpMethod.DELETE.equals(method) || HttpMethod.PATCH.equals(method);
    }

    private String resolveClientKey(ServerHttpRequest request) {
        // Nếu có Authorization Bearer token, ưu tiên dùng hash token để nhận diện chính xác người dùng
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 20) {
            String token = authHeader.substring(7).trim();
            return "user:" + Integer.toHexString(token.hashCode());
        }

        // Lấy IP từ X-Forwarded-For hoặc Remote Address
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return "ip:" + xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return "ip:" + xRealIp.trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }

        return "ip:anonymous";
    }

    private void cleanupExpiredBuckets() {
        try {
            long currentWindow = System.currentTimeMillis() / WINDOW_MS;
            requestCounts.entrySet().removeIf(entry -> (currentWindow - entry.getValue().window) > 2);
        } catch (Exception e) {
            log.warn("[RateLimit] Lỗi khi dọn dẹp bucket: {}", e.getMessage());
        }
    }

    private static class WindowCounter {
        final long window;
        final AtomicInteger count;

        WindowCounter(long window, AtomicInteger count) {
            this.window = window;
            this.count = count;
        }
    }
}
