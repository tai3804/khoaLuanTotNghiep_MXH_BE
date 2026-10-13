package iuh.fit.commonframework.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bảo vệ các API nội bộ /api/v1/internal/**: chỉ chấp nhận request mang đúng khoá dịch vụ trong header X-Internal-Key.
 * Gateway không route các đường dẫn này, nhưng cổng của từng service vẫn mở khi gọi trực tiếp; trước đây SecurityConfig
 * cho phép công khai nên ai biết cổng cũng đổi được quyền người dùng.
 * Các service gọi nhau (Feign) phải gửi header này (xem FeignClientConfig của admin-service và ai-service).
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    @Value("${app.security.internal-api-key:kltn-internal-dev-key}")
    private String internalApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), internalApiKey.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected internal API call without a valid {} header: {} {}", HEADER, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Bạn không có quyền thực hiện thao tác này\"}");
    }
}
