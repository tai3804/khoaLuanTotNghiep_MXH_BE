package iuh.fit.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test cho RateLimitingGlobalFilter (Token Bucket) tại API Gateway")
class RateLimitingGlobalFilterTest {

    private RateLimitingGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingGlobalFilter();
        ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(filter, "authCapacity", 3);
        ReflectionTestUtils.setField(filter, "authRefillSeconds", 30);
        ReflectionTestUtils.setField(filter, "aiCapacity", 2);
        ReflectionTestUtils.setField(filter, "aiRefillSeconds", 10);
        ReflectionTestUtils.setField(filter, "mutationCapacity", 5);
        ReflectionTestUtils.setField(filter, "mutationRefillSeconds", 30);
        ReflectionTestUtils.setField(filter, "generalCapacity", 10);
        ReflectionTestUtils.setField(filter, "generalRefillSeconds", 1);
    }

    @Test
    @DisplayName("Cho phép yêu cầu đi qua khi còn token trong Bucket")
    void shouldAllowRequestUnderLimit() {
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            filterChainExecuted.set(true);
            return Mono.empty();
        };

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertTrue(filterChainExecuted.get(), "Yêu cầu hợp lệ phải được chuyển tiếp qua GatewayFilterChain");
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Chặn và trả về HTTP 429 Too Many Requests khi cạn token (vượt capacity)")
    void shouldBlockAndReturn429WhenLimitExceeded() {
        GatewayFilterChain chain = exchange -> Mono.empty();

        // Tiêu thụ hết 3 token ban đầu trong bucket (authCapacity = 3)
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/auth/login")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.50", 12345))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
        }

        // Yêu cầu thứ 4 khi cạn token -> phải bị chặn HTTP 429 với Retry-After đúng thời gian nạp token
        MockServerHttpRequest blockedReq = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.50", 12345))
                .build();
        MockServerWebExchange blockedExchange = MockServerWebExchange.from(blockedReq);

        filter.filter(blockedExchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blockedExchange.getResponse().getStatusCode(),
                "Phải trả về HTTP 429 khi hết token trong Token Bucket");

        HttpHeaders headers = blockedExchange.getResponse().getHeaders();
        assertNotNull(headers.getFirst("Retry-After"), "Phải có header Retry-After");
        assertEquals("3", headers.getFirst("X-RateLimit-Limit"), "Header X-RateLimit-Limit phải khớp capacity cấu hình");
        assertEquals("0", headers.getFirst("X-RateLimit-Remaining"), "Header X-RateLimit-Remaining phải bằng 0");
    }

    @Test
    @DisplayName("Miễn trừ Rate Limit cho các kết nối WebSocket và Actuator")
    void shouldBypassRateLimitForWebSocketAndActuator() {
        GatewayFilterChain chain = exchange -> Mono.empty();

        // Gửi 10 yêu cầu WebSocket /ws-chat
        for (int i = 0; i < 10; i++) {
            MockServerHttpRequest wsReq = MockServerHttpRequest.get("/ws-chat/info")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.99", 54321))
                    .build();
            MockServerWebExchange wsExchange = MockServerWebExchange.from(wsReq);

            filter.filter(wsExchange, chain).block();
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, wsExchange.getResponse().getStatusCode(),
                    "WebSocket handshake/info không được bị chặn bởi Rate Limiter");
        }
    }

    @Test
    @DisplayName("Cho phép toàn bộ yêu cầu khi tắt cờ RATE_LIMIT_ENABLED")
    void shouldAllowAllWhenRateLimitDisabled() {
        ReflectionTestUtils.setField(filter, "rateLimitEnabled", false);
        GatewayFilterChain chain = exchange -> Mono.empty();

        for (int i = 0; i < 10; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/auth/login")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.77", 9999))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);

            filter.filter(ex, chain).block();
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getResponse().getStatusCode(),
                    "Khi tắt cờ RATE_LIMIT_ENABLED thì không được chặn request");
        }
    }

    @Test
    @DisplayName("Đăng nhập: đổi Bearer token giả mỗi request không làm bucket mới (giới hạn theo IP)")
    void authLimitIgnoresForgedBearerTokens() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        int blocked = 0;
        for (int i = 0; i < 10; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/auth/login")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer forged-token-number-" + i + "-xxxxxxxxxx")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.60", 1111))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            if (ex.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) blocked++;
        }
        assertEquals(7, blocked, "authCapacity = 3 nên 7 request còn lại phải bị chặn dù mỗi request một token khác nhau");
    }

    @Test
    @DisplayName("Nhóm khác: bucket chặn theo IP vẫn giới hạn khi mỗi request dùng một token giả")
    void ipGuardLimitsForgedBearerTokens() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        int blocked = 0;
        for (int i = 0; i < 80; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/posts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer forged-token-number-" + i + "-xxxxxxxxxx")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.61", 2222))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            if (ex.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) blocked++;
        }
        assertTrue(blocked > 0, "Không có token giả nào được phép vượt quá hạn mức theo IP");
    }

    @Test
    @DisplayName("X-Forwarded-For: chỉ tin phần tử do proxy tin cậy thêm vào, không tin phần tử client tự gửi")
    void spoofedForwardedForDoesNotChangeBucket() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        int blocked = 0;
        for (int i = 0; i < 6; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/auth/login")
                    .header("X-Forwarded-For", "1.1.1." + i + ", 203.0.113.9")   // phần tử đầu do client giả, cuối do proxy thêm
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.62", 3333))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            if (ex.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) blocked++;
        }
        assertEquals(3, blocked, "authCapacity = 3: 3 request cuối phải bị chặn dù phần tử đầu của X-Forwarded-For luôn khác");
    }

    private static MockServerWebExchange postAs(String path, String token, String ip) {
        MockServerHttpRequest req = MockServerHttpRequest.post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .remoteAddress(new java.net.InetSocketAddress(ip, 5555))
                .build();
        return MockServerWebExchange.from(req);
    }

    @Test
    @DisplayName("Ghi nhận lượt xem (POST /posts/{id}/view, /posts/seen) không tính vào hạn mức thao tác ghi")
    void viewTrackingDoesNotUseMutationBudget() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        String token = "viewer-token-xxxxxxxxxxxxxxxxxxxx";

        // Bảng tin tải 8 bài: 8 lượt xem + 1 lô "seen", nhiều hơn mutationCapacity = 5
        for (int i = 0; i < 8; i++) {
            MockServerWebExchange ex = postAs("/api/v1/posts/00000000-0000-0000-0000-00000000000" + i + "/view", token, "10.0.0.70");
            filter.filter(ex, chain).block();
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getResponse().getStatusCode(), "Lượt xem thứ " + (i + 1) + " không được bị chặn");
        }
        MockServerWebExchange seen = postAs("/api/v1/posts/seen", token, "10.0.0.70");
        filter.filter(seen, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, seen.getResponse().getStatusCode(), "Lô seen không được bị chặn");

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> buckets = (java.util.Map<String, Object>) ReflectionTestUtils.getField(filter, "tokenBuckets");
        assertTrue(buckets.keySet().stream().noneMatch(k -> k.startsWith("mutation")), "Lượt xem không được tạo bucket mutation");

        // Hạn mức thao tác ghi vẫn còn nguyên: người dùng vẫn đăng bài, bình luận được
        MockServerWebExchange create = postAs("/api/v1/posts", token, "10.0.0.70");
        filter.filter(create, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, create.getResponse().getStatusCode());
        assertTrue(buckets.keySet().stream().anyMatch(k -> k.startsWith("mutation:user:")), "Đăng bài vẫn thuộc nhóm mutation");
    }

    @Test
    @DisplayName("Thao tác ghi thật trên bài viết vẫn bị giới hạn theo nhóm mutation")
    void realPostMutationsStillLimited() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        String token = "writer-token-xxxxxxxxxxxxxxxxxxxx";
        int blocked = 0;
        for (int i = 0; i < 7; i++) {
            MockServerWebExchange ex = postAs("/api/v1/posts/00000000-0000-0000-0000-000000000001/reactions", token, "10.0.0.71");
            filter.filter(ex, chain).block();
            if (ex.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) blocked++;
        }
        assertEquals(2, blocked, "mutationCapacity = 5 nên 2 request cuối phải bị chặn");
    }

    @Test
    @DisplayName("Dọn dẹp: bucket rảnh quá lâu và đã đầy token phải bị xoá khỏi bộ nhớ")
    void idleFullBucketsAreCleanedUp() {
        GatewayFilterChain chain = exchange -> Mono.empty();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/posts")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.63", 4444))
                .build();
        filter.filter(MockServerWebExchange.from(req), chain).block();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> buckets = (java.util.Map<String, Object>) ReflectionTestUtils.getField(filter, "tokenBuckets");
        assertEquals(1, buckets.size());

        // Giả lập bucket đã không được dùng 10 phút
        Object bucket = buckets.values().iterator().next();
        long tenMinutesAgo = System.currentTimeMillis() - 600_000L;
        ReflectionTestUtils.setField(bucket, "lastAccessTimestamp", tenMinutesAgo);
        ReflectionTestUtils.setField(bucket, "lastRefillTimestamp", tenMinutesAgo);
        ReflectionTestUtils.invokeMethod(filter, "cleanupExpiredBuckets");

        assertTrue(buckets.isEmpty(), "Bucket rảnh và đầy token phải được dọn");
    }
}
