package iuh.fit.commonframework.infrastructure.config;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Cấu hình hệ thống được áp dụng ở máy chủ: bảo trì, dung lượng tải lên")
class SystemConfigFiltersTest {

    private static SystemConfigReader reader(Map<String, String> configs) {
        RedisCacheService redis = mock(RedisCacheService.class);
        when(redis.get(SystemConfigReader.REDIS_KEY)).thenReturn(configs);
        return new SystemConfigReader(redis);
    }

    private static void loginAs(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null,
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Đọc cấu hình: kiểu boolean/số, khoá thiếu hoặc Redis lỗi thì dùng mặc định")
    void readerDefaults() {
        SystemConfigReader r = reader(Map.of("maintenance_mode", "true", "max_upload_size_mb", "abc"));
        assertTrue(r.flag("maintenance_mode", false));
        assertTrue(r.flag("allow_registration", true));
        assertEquals(25, r.intValue("max_upload_size_mb", 25));

        RedisCacheService broken = mock(RedisCacheService.class);
        when(broken.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertFalse(new SystemConfigReader(broken).flag("maintenance_mode", false));
    }

    @Test
    @DisplayName("Bảo trì: người dùng thường nhận 503, ADMIN/MODERATOR và API xác thực/quản trị vẫn qua")
    void maintenance() throws Exception {
        MaintenanceModeFilter filter = new MaintenanceModeFilter(reader(Map.of("maintenance_mode", "true")));

        loginAs("ROLE_USER");
        MockHttpServletResponse userRes = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), userRes, new MockFilterChain());
        assertEquals(503, userRes.getStatus());
        assertTrue(userRes.getContentAsString().contains("bảo trì"));

        MockHttpServletResponse authRes = new MockHttpServletResponse();
        MockFilterChain authChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"), authRes, authChain);
        assertNotNull(authChain.getRequest(), "API đăng nhập không bị chặn (auth-service tự kiểm tra vai trò)");

        loginAs("ROLE_USER", "ROLE_ADMIN");
        MockFilterChain adminChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), new MockHttpServletResponse(), adminChain);
        assertNotNull(adminChain.getRequest(), "ADMIN vẫn dùng được hệ thống khi bảo trì");
    }

    @Test
    @DisplayName("Không bảo trì: không chặn ai")
    void noMaintenance() throws Exception {
        MaintenanceModeFilter filter = new MaintenanceModeFilter(reader(Map.of("maintenance_mode", "false")));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("Tải lên: tệp vượt max_upload_size_mb bị 413, tệp nhỏ hơn đi tiếp")
    void uploadLimit() throws Exception {
        UploadLimitFilter filter = new UploadLimitFilter(reader(Map.of("max_upload_size_mb", "1")));

        MockMultipartHttpServletRequest big = new MockMultipartHttpServletRequest();
        big.setMethod("POST");
        big.setRequestURI("/api/v1/posts");
        big.setContentType("multipart/form-data; boundary=x");
        MockPart bigPart = new MockPart("files", "anh-lon.jpg", new byte[2 * 1024 * 1024]);
        big.addPart(bigPart);
        MockHttpServletResponse bigRes = new MockHttpServletResponse();
        filter.doFilter(big, bigRes, new MockFilterChain());
        assertEquals(413, bigRes.getStatus());
        assertTrue(bigRes.getContentAsString().contains("anh-lon.jpg"));

        MockMultipartHttpServletRequest small = new MockMultipartHttpServletRequest();
        small.setMethod("POST");
        small.setContentType("multipart/form-data; boundary=x");
        small.addPart(new MockPart("files", "anh-nho.jpg", new byte[200 * 1024]));
        small.addPart(new MockPart("content", null, "nội dung".getBytes()));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(small, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("Request không phải multipart thì không kiểm tra")
    void notMultipart() throws Exception {
        UploadLimitFilter filter = new UploadLimitFilter(reader(Map.of("max_upload_size_mb", "1")));
        MockHttpServletRequest json = new MockHttpServletRequest("POST", "/api/v1/posts");
        json.setContentType("application/json");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(json, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        assertEquals(List.of(), List.of());
    }
}
