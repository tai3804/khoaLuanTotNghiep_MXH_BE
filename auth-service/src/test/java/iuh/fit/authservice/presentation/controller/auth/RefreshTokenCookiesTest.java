package iuh.fit.authservice.presentation.controller.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cookie refresh token tách riêng trang người dùng và trang quản trị")
class RefreshTokenCookiesTest {

    private static MockHttpServletRequest request(String app) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (app != null) req.addHeader(RefreshTokenCookies.APP_HEADER, app);
        // Trình duyệt gửi cả hai cookie vì cùng tên miền API
        req.setCookies(new Cookie("refreshToken", "token-cua-nguoi-dung"), new Cookie("adminRefreshToken", "token-cua-quan-tri"));
        return req;
    }

    @Test
    @DisplayName("Trang người dùng (không có header) đọc và ghi cookie refreshToken")
    void userApp() {
        MockHttpServletRequest req = request(null);
        assertEquals("token-cua-nguoi-dung", RefreshTokenCookies.read(req));
        MockHttpServletResponse res = new MockHttpServletResponse();
        RefreshTokenCookies.write(req, res, "moi", 60);
        assertTrue(res.getHeader(HttpHeaders.SET_COOKIE).startsWith("refreshToken=moi"));
    }

    @Test
    @DisplayName("Trang quản trị (X-Client-App: ADMIN) chỉ đọc và ghi cookie adminRefreshToken")
    void adminApp() {
        MockHttpServletRequest req = request("ADMIN");
        assertEquals("token-cua-quan-tri", RefreshTokenCookies.read(req));
        MockHttpServletResponse res = new MockHttpServletResponse();
        RefreshTokenCookies.write(req, res, "moi", 60);
        String header = res.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(header.startsWith("adminRefreshToken=moi"), header);
        assertTrue(header.contains("HttpOnly"));
    }

    @Test
    @DisplayName("Không có cookie của đúng ứng dụng thì không lấy nhầm cookie của ứng dụng kia")
    void noCrossRead() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RefreshTokenCookies.APP_HEADER, "ADMIN");
        req.setCookies(new Cookie("refreshToken", "token-cua-nguoi-dung"));
        assertNull(RefreshTokenCookies.read(req));
    }
}
