package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.entities.SystemConfig;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateSystemConfigsCommand {

    private final SettingsRepository settingsRepository;

    @Transactional
    public void execute(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";

            SystemConfig config = settingsRepository.findConfigByKey(key)
                    .orElse(SystemConfig.builder().key(key).description("").build());

            config.setValue(val);
            settingsRepository.saveConfig(config);
        }
    }
}
