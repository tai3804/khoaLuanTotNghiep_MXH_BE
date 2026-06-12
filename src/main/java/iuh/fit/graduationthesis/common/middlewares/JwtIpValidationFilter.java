package iuh.fit.graduationthesis.common.middlewares;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtIpValidationFilter extends OncePerRequestFilter {

    private final MessageSource messageSource;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.equals("/favicon.ico")
                || path.startsWith("/static/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Thuật toán lấy IP Client chuẩn công nghiệp (Xử lý qua Proxy/Gateway/Nginx)
        String clientIp = request.getHeader("X-Forwarded-For");

        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else {
            // Header X-Forwarded-For có thể chứa chuỗi nhiều IP dạng: "Client, Proxy1, Proxy2"
            // Ta luôn lấy IP đầu tiên chính là IP gốc của Client
            clientIp = clientIp.split(",")[0].trim();
        }

        // Đưa IP vào log để bạn dễ dàng debug khi chạy thử
        log.info("[Auth Filter]: Request from IP: {}", clientIp);

        // 2. Kiểm tra bảo mật: Nếu request có mang theo Token, tiến hành so sánh chéo IP
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            // Lấy IP đã được ghim trong Token lúc Login (Giả sử bạn đặt key là "client_ip")
            String ipInToken = jwt.getClaimAsString("client_ip");

            if (ipInToken != null && !ipInToken.equals(clientIp)) {
                log.warn("[Auth Filter]: Token IP mismatch! Token IP: {}, Actual IP: {}", ipInToken, clientIp);

                String errorMessage = messageSource.getMessage(
                        "error.token_ip_mismatch", null, "Token is not valid for this network address",
                        LocaleContextHolder.getLocale());

                // Trả về lỗi 403 Forbidden lập tức, chặn không cho vào API Controller
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\": \"" + errorMessage + "\"}");
                return;
            }
        }

        // Cho phép Request hợp lệ đi tiếp qua màng lọc
        filterChain.doFilter(request, response);
    }
}