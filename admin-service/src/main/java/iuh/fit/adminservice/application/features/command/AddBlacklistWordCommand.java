package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddBlacklistWordCommand {
    private final SettingsRepository settingsRepository;

    public BlacklistedWord execute(String word) {
        BlacklistedWord bw = new BlacklistedWord();
        bw.setWord(word.toLowerCase().trim());
        return settingsRepository.saveBlacklistedWord(bw);
    }
}
