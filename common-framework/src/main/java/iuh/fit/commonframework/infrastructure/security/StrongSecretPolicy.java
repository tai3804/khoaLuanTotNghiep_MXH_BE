package iuh.fit.commonframework.infrastructure.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Quyết định có bắt buộc dùng secret mạnh (không chấp nhận giá trị mặc định dành cho dev) hay không.
 * <p>
 * Chế độ nghiêm ngặt bật khi profile {@code prod}/{@code production} đang active, hoặc khi đặt
 * {@code REQUIRE_STRONG_SECRETS=true}. Ở chế độ này, service từ chối khởi động nếu secret còn là giá trị mặc định,
 * thay vì chỉ ghi log cảnh báo rồi chạy với khoá/mật khẩu ai cũng biết.
 */
public final class StrongSecretPolicy {

    private StrongSecretPolicy() {
    }

    public static boolean isStrict(Environment environment) {
        if (environment == null) {
            return false;
        }
        return environment.acceptsProfiles(Profiles.of("prod", "production"))
                || Boolean.parseBoolean(environment.getProperty("REQUIRE_STRONG_SECRETS", "false"));
    }
}
