package iuh.fit.graduationthesis.auth.controllers;

import iuh.fit.graduationthesis.auth.dto.AuthResponse;
import iuh.fit.graduationthesis.auth.dto.LoginRequest;
import iuh.fit.graduationthesis.auth.dto.RegisterRequest;
import iuh.fit.graduationthesis.auth.services.AuthService;
import iuh.fit.graduationthesis.common.dto.responses.ApiResponse;
import iuh.fit.graduationthesis.common.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "API đăng ký, đăng nhập và quản lý phiên đăng nhập")
public class AuthController {

    AuthService authService;
    JwtUtil jwtUtil;
    MessageSource messageSource;

    @PostMapping("/register")
    @SecurityRequirements // Không cần Bearer Token → hiển thị là public trên Swagger
    @Operation(
            summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới với username và password. Mặc định role USER với quyền READ_PRIVILEGES."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng ký thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Username đã tồn tại hoặc dữ liệu không hợp lệ")
    })
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String message = messageSource.getMessage("success.register", null, LocaleContextHolder.getLocale());
        return ApiResponse.success(authService.register(request), message);
    }

    @PostMapping("/login")
    @SecurityRequirements // Không cần Bearer Token
    @Operation(
            summary = "Đăng nhập",
            description = """
                    Xác thực username + password. Trả về Access Token (JWT RSA256) trong body.
                    Refresh Token được gửi qua HttpOnly Cookie (không nằm trong response body).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công — trả Access Token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai username hoặc password")
    })
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(hidden = true) HttpServletResponse httpResponse) {

        String clientIp = extractClientIp(httpRequest);
        AuthResponse authResponse = authService.login(request, clientIp);

        // Gắn Refresh Token vào Cookie HttpOnly + SameSite
        ResponseCookie cookie = ResponseCookie.from("refresh_token", authResponse.getRefreshToken())
                .httpOnly(true)                  // JS không đọc được → chống XSS
                .secure(true)                    // Chỉ gửi qua HTTPS
                .sameSite("Strict")              // Chống CSRF
                .path("/api/v1/auth")            // Chỉ gửi cookie khi gọi các API auth
                .maxAge(Duration.ofDays(jwtUtil.getRefreshTokenExpirationDay()))
                .build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Xóa refresh token khỏi response body (chỉ trả trong cookie)
        authResponse.setRefreshToken(null);

        String message = messageSource.getMessage("success.login", null, LocaleContextHolder.getLocale());
        return ApiResponse.success(authResponse, message);
    }

    /**
     * Rút trích IP chuẩn công nghiệp (hỗ trợ Proxy/Load Balancer)
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}


