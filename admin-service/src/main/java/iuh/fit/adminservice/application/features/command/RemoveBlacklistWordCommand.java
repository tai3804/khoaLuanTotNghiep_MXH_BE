package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RemoveBlacklistWordCommand {
    private final SettingsRepository settingsRepository;

    public void execute(Long id) {
        settingsRepository.deleteBlacklistedWord(id);
    }
}
