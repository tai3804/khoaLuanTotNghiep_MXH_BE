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
}
