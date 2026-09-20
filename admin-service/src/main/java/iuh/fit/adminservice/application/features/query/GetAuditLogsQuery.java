package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.domain.entities.AuditLog;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAuditLogsQuery {
    private final SettingsRepository settingsRepository;

    public List<AuditLog> execute() {
        return settingsRepository.findAllAuditLogs();
    }
}
