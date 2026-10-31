package iuh.fit.commonframework.infrastructure.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Bảo vệ các API nội bộ /api/v1/internal/**: chỉ chấp nhận request mang đúng khoá dịch vụ trong header X-Internal-Key.
 * Gateway không route các đường dẫn này và luôn xoá X-Internal-Key từ client ngoài, nhưng cổng của từng service vẫn mở
 * khi gọi trực tiếp trong mạng nội bộ.
 * Các service gọi nhau (Feign) phải gửi header này.
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";
    private static final String DEFAULT_DEV_KEY = "kltn-internal-dev-key";

    @Value("${app.security.internal-api-key:kltn-internal-dev-key}")
    private String internalApiKey;

    @PostConstruct
    void warnIfUsingDefaultDevKey() {
        if (DEFAULT_DEV_KEY.equals(internalApiKey)) {
            log.warn("[Security] INTERNAL_API_KEY đang sử dụng giá trị mặc định dành cho môi trường dev. Hãy cấu hình INTERNAL_API_KEY riêng trên production!");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(INTERNAL_PREFIX)) {
            return false;
        }
        String providedKey = request.getHeader(HEADER);
        String authHeader = request.getHeader("Authorization");
        return providedKey == null || (authHeader != null && !authHeader.isBlank());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean isInternalEndpoint = uri != null && uri.startsWith(INTERNAL_PREFIX);
        String provided = request.getHeader(HEADER);

        boolean validKey = provided != null
                && internalApiKey != null
                && !internalApiKey.isBlank()
                && MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8),
                        internalApiKey.getBytes(StandardCharsets.UTF_8));

        if (validKey) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken internalAuth = new UsernamePasswordAuthenticationToken(
                        "internal-service",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_INTERNAL"),
                                new SimpleGrantedAuthority("INTERNAL")
                        )
                );
                SecurityContextHolder.getContext().setAuthentication(internalAuth);
            }
            chain.doFilter(request, response);
            return;
        }

        if (!isInternalEndpoint) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rejected internal API call without a valid {} header: {} {}", HEADER, request.getMethod(), uri);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Bạn không có quyền thực hiện thao tác này\"}");
    }
}
