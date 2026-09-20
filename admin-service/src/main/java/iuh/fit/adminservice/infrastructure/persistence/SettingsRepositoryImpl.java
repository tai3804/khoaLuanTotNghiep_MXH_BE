package iuh.fit.adminservice.infrastructure.persistence;

import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.entities.SystemConfig;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SettingsRepositoryImpl implements SettingsRepository {

    private final JpaAuditLogRepository jpaAuditLogRepository;
    private final JpaSystemConfigRepository jpaSystemConfigRepository;
    private final JpaBlacklistedWordRepository jpaBlacklistedWordRepository;

    @Override
    public BlacklistedWord saveBlacklistedWord(BlacklistedWord word) {
        return jpaBlacklistedWordRepository.save(word);
    }

    @Override
    public void deleteBlacklistedWord(Long id) {
        jpaBlacklistedWordRepository.deleteById(id);
    }

    @Override
    public List<BlacklistedWord> findAllBlacklistedWords() {
        return jpaBlacklistedWordRepository.findAll();
    }

    @Override
    public SystemConfig saveConfig(SystemConfig config) {
        return jpaSystemConfigRepository.save(config);
    }

    @Override
    public Optional<SystemConfig> findConfigByKey(String key) {
        return jpaSystemConfigRepository.findById(key);
    }

    @Override
    public List<SystemConfig> findAllConfigs() {
        return jpaSystemConfigRepository.findAll();
    }

    @Override
    public AuditLog saveAuditLog(AuditLog log) {
        return jpaAuditLogRepository.save(log);
    }

    @Override
    public List<AuditLog> findAllAuditLogs() {
        return jpaAuditLogRepository.findAllByOrderByCreatedAtDesc();
    }
}
