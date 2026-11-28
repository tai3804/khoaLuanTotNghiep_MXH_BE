package iuh.fit.commonframework.infrastructure.config;

import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Đọc cấu hình hệ thống do quản trị viên đặt ở trang "Cấu hình hệ thống" (admin-service lưu trong cơ sở dữ liệu và ghi bản sao
 * vào Redis khoá {@value #REDIS_KEY} mỗi lần đọc/lưu). Mọi service dùng lớp này để áp dụng cấu hình thật sự ở phía máy chủ:
 * bảo trì, cho phép đăng ký, dung lượng tải lên tối đa...
 *
 * <p>Giữ bản đọc trong bộ nhớ {@link #CACHE_MS} ms để không hỏi Redis ở mỗi request. Redis lỗi hoặc chưa có khoá thì dùng giá trị
 * mặc định (hệ thống vẫn chạy bình thường, không chặn nhầm người dùng).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigReader {

    public static final String REDIS_KEY = "system:configs";

    public static final String MAINTENANCE_MODE = "maintenance_mode";
    public static final String ALLOW_REGISTRATION = "allow_registration";
    public static final String AI_MODERATION_ENABLED = "ai_moderation_enabled";
    public static final String MAX_UPLOAD_SIZE_MB = "max_upload_size_mb";
    public static final String MAX_REPORTS_AUTO_HIDE = "max_reports_auto_hide";

    static final long CACHE_MS = 10_000;

    private final RedisCacheService redisCacheService;

    private volatile Map<String, String> cached = Map.of();
    private volatile long cachedAt = 0;

    public boolean flag(String key, boolean defaultValue) {
        String v = value(key);
        if (v == null || v.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(v.trim());
    }

    public int intValue(String key, int defaultValue) {
        String v = value(key);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String value(String key) {
        return snapshot().get(key);
    }

    /** Bỏ bản trong bộ nhớ: lần đọc sau lấy lại từ Redis (admin-service gọi ngay sau khi lưu cấu hình). */
    public void invalidate() {
        cachedAt = 0;
    }

    private Map<String, String> snapshot() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) return cached;
        try {
            Object raw = redisCacheService.get(REDIS_KEY);
            Map<String, String> next = new HashMap<>();
            if (raw instanceof Map<?, ?> m) {
                m.forEach((k, v) -> {
                    if (k != null && v != null) next.put(k.toString(), v.toString());
                });
            }
            cached = next;
        } catch (Exception e) {
            log.debug("Could not read system configs from Redis, keeping last known values: {}", e.getMessage());
        }
        cachedAt = now;
        return cached;
    }
}
