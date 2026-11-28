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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /** Số proxy tin cậy đứng trước gateway (đọc IP thật từ X-Forwarded-For). 0 = không tin X-Forwarded-For. */
    @Value("${rate-limit.trusted-proxy-hops:1}")
    private int trustedProxyHops = 1;

    /** Bucket chặn theo IP của các nhóm xác thực bằng token có hạn mức gấp chừng này lần bucket theo token. */
    private static final int IP_GUARD_FACTOR = 5;

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
        String clientIp = resolveClientIp(request);

        // Token ở header chưa được xác thực tại bước này, nên kẻ tấn công có thể gửi mỗi request một token giả để luôn
        // rơi vào bucket mới (né giới hạn và làm phình bộ nhớ). Hai biện pháp:
        //  - nhóm "auth" (đăng nhập, đăng ký, quên mật khẩu, MFA) chỉ tính theo IP, không tin token;
        //  - các nhóm khác thêm một bucket chặn theo IP với hạn mức rộng hơn, kiểm tra TRƯỚC khi tạo bucket theo token.
        boolean perIpOnly = "auth".equals(config.category());
        String bearerToken = perIpOnly ? null : extractBearerToken(request);
        String clientKey = bearerToken != null ? "user:" + tokenFingerprint(bearerToken) : "ip:" + clientIp;
        String bucketKey = config.category() + ":" + clientKey;

        if (bearerToken != null) {
            BucketConfig guard = new BucketConfig(config.category() + "-ipguard",
                    config.capacity() * IP_GUARD_FACTOR, config.refillSeconds() / IP_GUARD_FACTOR);
            ConsumptionResult guardResult = getOrCreateBucket(guard.category() + ":ip:" + clientIp, guard).tryConsume(1.0);
            if (!guardResult.allowed()) {
                return rejectTooManyRequests(exchange, path, guard.category() + ":ip:" + clientIp, guard, guardResult);
            }
        }

        ConsumptionResult result = getOrCreateBucket(bucketKey, config).tryConsume(1.0);

        // 4. Nếu vượt quá hạn mức, trả về HTTP 429 Too Many Requests
        if (!result.allowed()) {
            return rejectTooManyRequests(exchange, path, bucketKey, config, result);
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

    private TokenBucket getOrCreateBucket(String key, BucketConfig config) {
        return tokenBuckets.computeIfAbsent(key, k -> new TokenBucket(config.capacity(), config.refillSeconds()));
    }

    private Mono<Void> rejectTooManyRequests(ServerWebExchange exchange, String path, String bucketKey,
                                             BucketConfig config, ConsumptionResult result) {
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

        // Hạn mức AI chỉ dành cho request gọi Gemini (POST tóm tắt, trợ lý, kiểm duyệt...). Các GET của ai-service chỉ đọc DB
        // (thống kê, nhật ký kiểm duyệt, từ khoá ở trang quản trị) nên tính như request đọc thường, nếu không trang
        // "Kiểm duyệt AI" tải 3 bảng cùng lúc là chạm ngưỡng 5 request / 10 giây và bị 429.
        if (path.startsWith("/api/v1/ai") && !HttpMethod.GET.equals(method)) {
            return new BucketConfig("ai", aiCapacity, aiRefillSeconds);
        }

        // Ghi nhận lượt xem (POST /posts/{id}/view, /posts/seen) do bảng tin tự gửi cho mỗi bài hiện lên màn hình,
        // không phải thao tác của người dùng: không tính vào hạn mức mutation (10 request, 30 giây nạp 1) để không
        // bị 429 hàng loạt và không làm cạn hạn mức đăng bài, bình luận, thả cảm xúc. Vẫn bị giới hạn theo nhóm general.
        if (HttpMethod.POST.equals(method) && isViewTrackingPath(path)) {
            return generalBucket();
        }

        if (isMutationMethod(method) && (path.startsWith("/api/v1/posts") || path.startsWith("/api/v1/media")
                || path.startsWith("/api/v1/groups") || path.startsWith("/api/v1/comments"))) {
            return new BucketConfig("mutation", mutationCapacity, mutationRefillSeconds);
        }

        return generalBucket();
    }

    private BucketConfig generalBucket() {
        // Với general (GET): Tốc độ nạp 30 token/giây (0.033s/token) để client SPA tải mượt mà nhiều widget cùng lúc mà không bị 429
        double generalSecondsPerToken = Math.min((double) generalRefillSeconds, 0.033);
        return new BucketConfig("general", generalCapacity, generalSecondsPerToken);
    }

    private static final java.util.regex.Pattern POST_VIEW_PATH = java.util.regex.Pattern.compile("^/api/v1/posts/[^/]+/view/?$");

    private static boolean isViewTrackingPath(String path) {
        return path.equals("/api/v1/posts/seen") || path.equals("/api/v1/posts/seen/") || POST_VIEW_PATH.matcher(path).matches();
    }

    private boolean isMutationMethod(HttpMethod method) {
        return HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)
                || HttpMethod.DELETE.equals(method) || HttpMethod.PATCH.equals(method);
    }

    private static String extractBearerToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 20) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    /** Dấu vân tay của token: 8 byte đầu của SHA-256 (64 bit), thay cho String.hashCode() 32 bit dễ va chạm và dễ đoán. */
    private static String tokenFingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(token.hashCode());
        }
    }

    /**
     * IP của người gọi. Phần tử đầu của X-Forwarded-For do chính client tự gửi nên có thể giả mạo để đổi IP mỗi request; phần tử
     * do proxy tin cậy gần nhất thêm vào mới đáng tin. Với {@code trustedProxyHops} proxy phía trước gateway, IP thật là phần tử
     * thứ {@code trustedProxyHops} tính từ cuối. Đặt 0 để bỏ qua X-Forwarded-For (gateway nhận kết nối trực tiếp).
     */
    private String resolveClientIp(ServerHttpRequest request) {
        if (trustedProxyHops > 0) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] parts = xForwardedFor.split(",");
                int index = Math.max(0, parts.length - trustedProxyHops);
                String candidate = parts[index].trim();
                if (!candidate.isEmpty()) return candidate;
            }
            String xRealIp = request.getHeaders().getFirst("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "anonymous";
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
        /** Lần cuối có request dùng bucket này. Tách khỏi lastRefillTimestamp vì refill() luôn cập nhật mốc nạp. */
        private long lastAccessTimestamp;

        public TokenBucket(double capacity, double refillSecondsPerToken) {
            this.capacity = capacity;
            this.refillSecondsPerToken = Math.max(0.001, refillSecondsPerToken);
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
            this.lastAccessTimestamp = this.lastRefillTimestamp;
        }

        public synchronized ConsumptionResult tryConsume(double tokensToConsume) {
            refill();
            lastAccessTimestamp = lastRefillTimestamp;
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
            // Trước đây so với lastRefillTimestamp (vừa bị refill() ghi đè bằng "bây giờ") nên điều kiện không bao giờ đúng
            // và bucket không bao giờ được dọn: rò rỉ bộ nhớ theo số IP/token từng gọi.
            refill();
            return (now - lastAccessTimestamp) > maxIdleMs && tokens >= (capacity - 0.001);
        }
    }

    private record ConsumptionResult(boolean allowed, int remaining, long resetSeconds, long retryAfter) {}
    private record BucketConfig(String category, int capacity, double refillSeconds) {}
}
