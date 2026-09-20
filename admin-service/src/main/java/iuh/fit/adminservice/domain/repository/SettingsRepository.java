package iuh.fit.adminservice.domain.repository;

import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.entities.SystemConfig;

import java.util.List;
import java.util.Optional;

public interface SettingsRepository {
    // Blacklist
    BlacklistedWord saveBlacklistedWord(BlacklistedWord word);
    void deleteBlacklistedWord(Long id);
    List<BlacklistedWord> findAllBlacklistedWords();

    // Config
    SystemConfig saveConfig(SystemConfig config);
    Optional<SystemConfig> findConfigByKey(String key);
    List<SystemConfig> findAllConfigs();

    // Audit Log
    AuditLog saveAuditLog(AuditLog log);
    List<AuditLog> findAllAuditLogs();
}
