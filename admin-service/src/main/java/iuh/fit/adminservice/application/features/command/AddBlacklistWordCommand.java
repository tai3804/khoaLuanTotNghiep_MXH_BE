package iuh.fit.adminservice.application.features.command;

import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import iuh.fit.adminservice.domain.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AddBlacklistWordCommand {
    private final SettingsRepository settingsRepository;

    public BlacklistedWord execute(String word) {
        if (word == null) {
            return null;
        }
        String normalizedWord = word.trim();
        if (normalizedWord.startsWith("\"") && normalizedWord.endsWith("\"") && normalizedWord.length() >= 2) {
            normalizedWord = normalizedWord.substring(1, normalizedWord.length() - 1);
        }
        normalizedWord = normalizedWord.toLowerCase().trim();
        if (normalizedWord.isBlank()) {
            return null;
        }

        Optional<BlacklistedWord> existing = settingsRepository.findBlacklistedWordByWordIgnoreCase(normalizedWord);
        if (existing.isPresent()) {
            return existing.get();
        }

        BlacklistedWord bw = new BlacklistedWord();
        bw.setWord(normalizedWord);
        return settingsRepository.saveBlacklistedWord(bw);
    }
}
