package iuh.fit.commonframework.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Chế độ bảo trì ({@code maintenance_mode = true}): mọi API của người dùng thường trả 503; quản trị viên và kiểm duyệt viên vẫn
 * dùng được để kiểm tra hệ thống. Không chặn: xác thực (đăng nhập do auth-service tự kiểm tra vai trò), API nội bộ giữa các
 * service, API quản trị (đã giới hạn vai trò), cấu hình công khai (giao diện cần để hiện màn hình bảo trì), tệp media, actuator.
 *
 * <p>Chạy sau chuỗi lọc bảo mật (thứ tự thấp hơn FilterChainProxy) nên đã biết người gọi là ai.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    static final List<String> ALWAYS_ALLOWED = List.of(
            "/api/v1/auth/",
            "/api/v1/internal/",
            "/api/v1/admin/",
            "/api/v1/posts/admin/",
            "/api/v1/users/admin/",
            "/api/v1/groups/admin/",
            "/api/v1/media/files/",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final SystemConfigReader systemConfigReader;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!systemConfigReader.flag(SystemConfigReader.MAINTENANCE_MODE, false)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || isAlwaysAllowed(request.getRequestURI())
                || isStaff(SecurityContextHolder.getContext().getAuthentication())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "300");
        response.getWriter().write("{\"code\":503,\"message\":\"Hệ thống đang bảo trì, vui lòng quay lại sau.\",\"maintenance\":true}");
    }

    static boolean isAlwaysAllowed(String uri) {
        if (uri == null) return true;
        for (String prefix : ALWAYS_ALLOWED) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }

    static boolean isStaff(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String role = a.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_MODERATOR".equals(role)) return true;
        }
        return false;
    }
}
