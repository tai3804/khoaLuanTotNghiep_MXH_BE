package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.domain.entities.SystemConfig;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetSystemConfigsQuery {

    private final SettingsRepository settingsRepository;

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
        return configMap;
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
