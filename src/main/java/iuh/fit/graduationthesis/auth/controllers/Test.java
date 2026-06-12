package iuh.fit.graduationthesis.auth.controllers;

import iuh.fit.graduationthesis.auth.services.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest; // <-- Thêm import này
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/test")
@Tag(name = "Test", description = "Endpoint test JWT token generation")
public class Test {
    private final JwtService jwtService;

    /**
     * Test endpoint để kiểm tra JWT configuration và tự động lấy IP
     */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Test JWT", description = "Sinh JWT token demo để kiểm tra cấu hình RSA key")
    public String test(@Parameter(hidden = true) HttpServletRequest request) {

        // 1. Tự động lấy IP từ request
        String clientIp = getClientIp(request);

        log.info("[Test API] - Đang sinh token cho User từ IP: {}", clientIp);

        // 2. Truyền IP tự lấy được vào hàm sinh token
        return "Test successful! JWT token: " + jwtService.generateToken(
                "test-user-id",
                Set.of("USER"),
                Set.of("READ_PRIVILEGES"),
                clientIp);
    }

    /**
     * Helper rút trích IP chuẩn công nghiệp (Hỗ trợ cả khi chạy qua Proxy/Load Balancer)
     */
    private String getClientIp(HttpServletRequest request) {
        // Kiểm tra xem request có đi qua các tầng Proxy (như Nginx, Cloudflare, AWS ALB) không
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        // Nếu không đi qua proxy nào, lấy IP trực tiếp kết nối đến server
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // Nếu chuỗi X-Forwarded-For chứa danh sách IP (dạng "IP_Client, IP_Proxy1, IP_Proxy2"), lấy cái đầu tiên
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}