package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.entities.SystemConfig;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lưu cấu hình hệ thống. Đọc toàn bộ cấu hình hiện có một lần (thay vì một truy vấn cho mỗi khoá) và kiểm tra giá trị trước khi
 * lưu: giá trị sai (vd dung lượng âm) bị từ chối 400 thay vì được lưu rồi làm hỏng các service đang đọc.
 */
@Service
@RequiredArgsConstructor
public class UpdateSystemConfigsCommand {

    static final Set<String> BOOLEAN_KEYS = Set.of("maintenance_mode", "allow_registration", "ai_moderation_enabled",
            "announcement_is_active", "announcement_is_scheduled");

    private final SettingsRepository settingsRepository;

    @Transactional
    public void execute(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        configs.forEach(UpdateSystemConfigsCommand::validate);

        Map<String, SystemConfig> existing = settingsRepository.findAllConfigs().stream()
                .collect(Collectors.toMap(SystemConfig::getKey, Function.identity(), (a, b) -> a));

        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue().trim() : "";
            SystemConfig config = existing.getOrDefault(key, SystemConfig.builder().key(key).description("").build());
            if (val.equals(config.getValue())) continue; // không đổi thì không ghi
            config.setValue(val);
            settingsRepository.saveConfig(config);
        }
    }

    static void validate(String key, String value) {
        String v = value == null ? "" : value.trim();
        if (BOOLEAN_KEYS.contains(key) && !v.isEmpty() && !"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
            throw bad("Giá trị của " + key + " phải là true hoặc false");
        }
        if ("max_upload_size_mb".equals(key)) requireInt(key, v, 1, 150, "Dung lượng tải lên phải từ 1 đến 150 MB");
        if ("max_reports_auto_hide".equals(key)) requireInt(key, v, 1, 1000, "Ngưỡng tự ẩn bài phải từ 1 đến 1000 báo cáo");
        if ("system_announcement".equals(key) && v.length() > 500) throw bad("Thông báo hệ thống tối đa 500 ký tự");
    }

    private static void requireInt(String key, String v, int min, int max, String message) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) throw bad(message);
        } catch (NumberFormatException e) {
            throw bad(message);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
