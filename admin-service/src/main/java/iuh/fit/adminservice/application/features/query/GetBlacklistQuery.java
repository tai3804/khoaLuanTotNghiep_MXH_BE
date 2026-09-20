package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetBlacklistQuery {
    private final SettingsRepository settingsRepository;

    public List<BlacklistedWord> execute() {
        return settingsRepository.findAllBlacklistedWords();
    }
}
