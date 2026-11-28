package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.domain.entities.SystemConfig;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.config.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc cấu hình hệ thống từ cơ sở dữ liệu và ghi bản sao vào Redis ({@link SystemConfigReader#REDIS_KEY}) để các service khác áp
 * dụng: bảo trì (mọi service), cho phép đăng ký (auth-service), dung lượng tải lên (mọi API nhận tệp), ngưỡng tự ẩn bài
 * (admin-service), bật/tắt kiểm duyệt AI (ai-service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetSystemConfigsQuery {

    private final SettingsRepository settingsRepository;
    private final RedisCacheService redisCacheService;
    private final SystemConfigReader systemConfigReader;

    public Map<String, String> execute() {
        List<SystemConfig> configs = settingsRepository.findAllConfigs();
        Map<String, String> configMap = new HashMap<>();

        if (configs == null || configs.isEmpty()) {
            initDefaultConfigs();
            configs = settingsRepository.findAllConfigs();
        }

        for (SystemConfig config : configs) {
            configMap.put(config.getKey(), config.getValue());
        }
        publish(configMap);
        return configMap;
    }

    /** Admin-service khởi động: đẩy cấu hình hiện có lên Redis ngay, không chờ ai mở trang cấu hình. */
    @EventListener(ApplicationReadyEvent.class)
    public void publishOnStartup() {
        try {
            execute();
        } catch (Exception e) {
            log.warn("Could not publish system configs on startup: {}", e.getMessage());
        }
    }

    private void publish(Map<String, String> configMap) {
        try {
            redisCacheService.set(SystemConfigReader.REDIS_KEY, new HashMap<>(configMap));
            systemConfigReader.invalidate();
        } catch (Exception e) {
            log.warn("Could not publish system configs to Redis: {}", e.getMessage());
        }
    }

    private void initDefaultConfigs() {
        settingsRepository.saveConfig(SystemConfig.builder().key("maintenance_mode").value("false").description("Chế độ bảo trì hệ thống").build());
        settingsRepository.saveConfig(SystemConfig.builder().key("allow_registration").value("true").description("Cho phép đăng ký tài khoản mới").build());
        settingsRepository.saveConfig(SystemConfig.builder().key("ai_moderation_enabled").value("true").description("Tự động kiểm duyệt bài viết bằng AI").build());
        settingsRepository.saveConfig(SystemConfig.builder().key("max_upload_size_mb").value("25").description("Dung lượng file tải lên tối đa (MB)").build());
        settingsRepository.saveConfig(SystemConfig.builder().key("system_announcement").value("").description("Thông báo banner toàn hệ thống").build());
        settingsRepository.saveConfig(SystemConfig.builder().key("max_reports_auto_hide").value("5").description("Số lượt báo cáo vi phạm trước khi tạm ẩn bài").build());
    }
}
