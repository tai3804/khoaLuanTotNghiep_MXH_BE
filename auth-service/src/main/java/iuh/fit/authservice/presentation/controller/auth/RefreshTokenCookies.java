package iuh.fit.authservice.presentation.controller.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Đọc/ghi cookie HttpOnly chứa refresh token, tách riêng theo ứng dụng.
 *
 * <p>Trang người dùng và trang quản trị cùng gọi một API nên trình duyệt lưu cookie theo tên miền của API, dùng chung cho cả hai
 * trang (cookie không phân biệt cổng/trang gọi). Nếu cùng tên cookie, đăng nhập trang quản trị sẽ ghi đè phiên của trang người
 * dùng và lần làm mới kế tiếp ở trang người dùng nhận token của tài khoản quản trị. Vì vậy trang quản trị gửi header
 * {@code X-Client-App: ADMIN} và dùng cookie riêng {@value #ADMIN_COOKIE}; các ứng dụng khác giữ cookie {@value #USER_COOKIE}.
 */
public final class RefreshTokenCookies {

    public static final String APP_HEADER = "X-Client-App";
    public static final String USER_COOKIE = "refreshToken";
    public static final String ADMIN_COOKIE = "adminRefreshToken";

    private RefreshTokenCookies() {
    }

    public static String cookieName(HttpServletRequest request) {
        return request != null && "ADMIN".equalsIgnoreCase(request.getHeader(APP_HEADER)) ? ADMIN_COOKIE : USER_COOKIE;
    }

    /** Refresh token trong cookie của đúng ứng dụng đang gọi; không có thì null. */
    public static String read(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) return null;
        String name = cookieName(request);
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue().trim();
        }
        return null;
    }

    /** Ghi (hoặc xoá khi {@code maxAgeSeconds} = 0) cookie refresh token của ứng dụng đang gọi. */
    public static void write(HttpServletRequest request, HttpServletResponse response, String token, long maxAgeSeconds) {
        boolean isHttps = (request != null && request.isSecure())
                || (request != null && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        ResponseCookie cookie = ResponseCookie.from(cookieName(request), token)
                .httpOnly(true)
                .secure(isHttps)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(isHttps ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
