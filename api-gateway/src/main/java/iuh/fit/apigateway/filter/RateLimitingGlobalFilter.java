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

/**
 * Bộ lọc giới hạn tần suất yêu cầu (Rate Limiting) tại API Gateway.
 * Sử dụng thuật toán Token Bucket (Thùng chứa token nạp dần theo thời gian thực)
 * phân cấp theo mức độ nhạy cảm của tài nguyên (Auth, AI, Mutation, General),
 * kết hợp cơ chế dọn dẹp bộ nhớ định kỳ.
 *
 * Toàn bộ tham số cấu hình được nạp động từ file .env / biến môi trường:
 * - RATE_LIMIT_ENABLED (true/false)
 * - RATE_LIMIT_AUTH_CAPACITY (mặc định 5)
 * - RATE_LIMIT_AUTH_REFILL_SECONDS (mặc định 30s nạp 1 token)
 * - RATE_LIMIT_MUTATION_CAPACITY (mặc định 10)
 * - RATE_LIMIT_MUTATION_REFILL_SECONDS (mặc định 30s nạp 1 token)
 * - RATE_LIMIT_AI_CAPACITY (mặc định 5)
 * - RATE_LIMIT_AI_REFILL_SECONDS (mặc định 10s nạp 1 token)
 * - RATE_LIMIT_GENERAL_CAPACITY (mặc định 60)
 * - RATE_LIMIT_GENERAL_REFILL_SECONDS (mặc định 1s nạp 1 token)
 */
@Component
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingGlobalFilter.class);

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.auth-capacity:5}")
    private int authCapacity;

    @Value("${rate-limit.auth-refill-seconds:30}")
    private int authRefillSeconds;

    @Value("${rate-limit.ai-capacity:5}")
    private int aiCapacity;

    @Value("${rate-limit.ai-refill-seconds:10}")
    private int aiRefillSeconds;

    @Value("${rate-limit.mutation-capacity:10}")
    private int mutationCapacity;

    @Value("${rate-limit.mutation-refill-seconds:30}")
    private int mutationRefillSeconds;

    @Value("${rate-limit.general-capacity:300}")
    private int generalCapacity;

    @Value("${rate-limit.general-refill-seconds:1}")
    private int generalRefillSeconds;

    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    public RateLimitingGlobalFilter() {
        // Định kỳ mỗi 60 giây dọn dẹp các bucket đã rảnh rỗi trên 2 phút và đầy token để giải phóng bộ nhớ
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

        // 3. Xác định nhóm giới hạn và cấu hình Token Bucket
        BucketConfig config = resolveBucketConfig(path, request.getMethod());
        String clientKey = resolveClientKey(request);
        String bucketKey = config.category() + ":" + clientKey;

        TokenBucket bucket = tokenBuckets.compute(bucketKey, (k, existing) -> {
            if (existing == null) {
                return new TokenBucket(config.capacity(), config.refillSeconds());
            }
            return existing;
        });

        ConsumptionResult result = bucket.tryConsume(1.0);

        // 4. Nếu vượt quá hạn mức, trả về HTTP 429 Too Many Requests
        if (!result.allowed()) {
            log.warn("[RateLimit-TokenBucket] Chặn yêu cầu quá giới hạn: key={}, path={}, retryAfter={}s",
                    bucketKey, path, result.retryAfter());

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Retry-After", String.valueOf(result.retryAfter()));
            headers.set("X-RateLimit-Limit", String.valueOf(config.capacity()));
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
            headers.set("Access-Control-Expose-Headers", "Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset");

            String bodyJson = String.format(
                    "{\"code\":429,\"message\":\"Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ %d giây trước khi thử lại.\",\"retryAfter\":%d}",
                    result.retryAfter(), result.retryAfter());
            byte[] bytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }

        // 5. Gắn các header RateLimit vào phản hồi để client theo dõi
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders resHeaders = exchange.getResponse().getHeaders();
            resHeaders.set("X-RateLimit-Limit", String.valueOf(config.capacity()));
            resHeaders.set("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            resHeaders.set("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
            resHeaders.set("Access-Control-Expose-Headers", "Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset");
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private boolean isWebSocketPath(String path) {
        return path.startsWith("/ws-chat") || path.startsWith("/ws-notifications") || path.startsWith("/ws-call");
    }

    private BucketConfig resolveBucketConfig(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/mfa")) {
            return new BucketConfig("auth", authCapacity, authRefillSeconds);
        }

        if (path.startsWith("/api/v1/ai")) {
            return new BucketConfig("ai", aiCapacity, aiRefillSeconds);
        }

        if (isMutationMethod(method) && (path.startsWith("/api/v1/posts") || path.startsWith("/api/v1/media")
                || path.startsWith("/api/v1/groups") || path.startsWith("/api/v1/comments"))) {
            return new BucketConfig("mutation", mutationCapacity, mutationRefillSeconds);
        }

        // Với general (GET): Tốc độ nạp 30 token/giây (0.033s/token) để client SPA tải mượt mà nhiều widget cùng lúc mà không bị 429
        double generalSecondsPerToken = Math.min((double) generalRefillSeconds, 0.033);
        return new BucketConfig("general", generalCapacity, generalSecondsPerToken);
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
            long now = System.currentTimeMillis();
            long maxIdleMs = 120_000L; // 2 phút không có hoạt động và đã đầy token
            tokenBuckets.entrySet().removeIf(entry -> entry.getValue().isIdleAndFull(now, maxIdleMs));
        } catch (Exception e) {
            log.warn("[RateLimit] Lỗi khi dọn dẹp bucket: {}", e.getMessage());
        }
    }

    /**
     * Cài đặt cấu trúc Token Bucket theo thời gian thực (Thread-Safe).
     */
    private static class TokenBucket {
        private final double capacity;
        private final double refillSecondsPerToken;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(double capacity, double refillSecondsPerToken) {
            this.capacity = capacity;
            this.refillSecondsPerToken = Math.max(0.001, refillSecondsPerToken);
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized ConsumptionResult tryConsume(double tokensToConsume) {
            refill();
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume;
                int remaining = (int) Math.floor(tokens);
                long resetSeconds = Math.max(0, (long) Math.ceil((capacity - tokens) * refillSecondsPerToken));
                return new ConsumptionResult(true, remaining, resetSeconds, 0);
            } else {
                double missing = tokensToConsume - tokens;
                long retryAfter = Math.max(1, (long) Math.ceil(missing * refillSecondsPerToken));
                long resetSeconds = Math.max(retryAfter, (long) Math.ceil((capacity - tokens) * refillSecondsPerToken));
                return new ConsumptionResult(false, 0, resetSeconds, retryAfter);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedMs = now - lastRefillTimestamp;
            if (elapsedMs > 0) {
                double elapsedSeconds = elapsedMs / 1000.0;
                double tokensToAdd = elapsedSeconds / refillSecondsPerToken;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }

        public synchronized boolean isIdleAndFull(long now, long maxIdleMs) {
            refill();
            return (now - lastRefillTimestamp) > maxIdleMs && tokens >= (capacity - 0.001);
        }
    }

    private record ConsumptionResult(boolean allowed, int remaining, long resetSeconds, long retryAfter) {}
    private record BucketConfig(String category, int capacity, double refillSeconds) {}
}
