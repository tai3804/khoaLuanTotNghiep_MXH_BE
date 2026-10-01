package iuh.fit.aiservice.application.service;

import iuh.fit.aiservice.application.engine.FastSensitiveWordFilter;
import iuh.fit.aiservice.domain.entities.SensitiveKeyword;
import iuh.fit.aiservice.domain.repository.SensitiveKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfLearningService {

    private final SensitiveKeywordRepository repository;
    private final FastSensitiveWordFilter fastFilter;

    @Value("${ai.moderation.auto-learn-enabled:true}")
    private boolean autoLearnEnabled;

    /**
     * Tự học các từ ngữ nhạy cảm mới được phát hiện từ Gemini AI hoặc Admin
     */
    @Transactional
    public void learnKeywords(List<String> keywords, String category, String severity, boolean isAutoLearned) {
        if (!autoLearnEnabled && isAutoLearned) {
            log.info("Auto-learning is disabled. Skipping learning for keywords: {}", keywords);
            return;
        }

        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        for (String rawKeyword : keywords) {
            String word = rawKeyword != null ? rawKeyword.toLowerCase().trim() : "";
            if (word.length() < 2 || word.length() > 50) {
                continue; // Bỏ qua từ quá ngắn hoặc quá dài
            }

            try {
                Optional<SensitiveKeyword> existing = repository.findByKeyword(word);
                if (existing.isPresent()) {
                    SensitiveKeyword kw = existing.get();
                    kw.setHitCount(kw.getHitCount() + 1);
                    kw.setLastTriggeredAt(LocalDateTime.now());
                    if (severity != null) {
                        kw.setSeverity(severity);
                    }
                    if (category != null && !"NONE".equalsIgnoreCase(category)) {
                        kw.setCategory(category);
                    }
                    repository.save(kw);
                    fastFilter.addOrUpdateWordInCache(word, kw.getCategory(), kw.getSeverity());
                    log.info("Incremented hit count for existing keyword: '{}' (hits={})", word, kw.getHitCount());
                } else {
                    SensitiveKeyword newKw = SensitiveKeyword.builder()
                            .keyword(word)
                            .category(category != null && !"NONE".equalsIgnoreCase(category) ? category : "PROFANITY")
                            .severity(severity != null ? severity : "MEDIUM")
                            .hitCount(1)
                            .isAutoLearned(isAutoLearned)
                            .status("ACTIVE")
                            .confidenceScore(isAutoLearned ? 0.85 : 1.0)
                            .lastTriggeredAt(LocalDateTime.now())
                            .build();

                    repository.save(newKw);
                    fastFilter.addOrUpdateWordInCache(word, newKw.getCategory(), newKw.getSeverity());
                    log.info("Successfully learned new {} keyword: '{}' [category={}, severity={}]",
                            isAutoLearned ? "AI auto-learned" : "manual admin", word, category, severity);
                }
            } catch (Exception e) {
                log.warn("Failed to learn keyword '{}': {}", word, e.getMessage());
            }
        }
    }

    @Transactional
    public SensitiveKeyword approveAutoLearnedKeyword(UUID id) {
        SensitiveKeyword kw = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Keyword not found with id: " + id));
        kw.setStatus("ACTIVE");
        kw.setConfidenceScore(1.0);
        SensitiveKeyword saved = repository.save(kw);
        fastFilter.addOrUpdateWordInCache(saved.getKeyword(), saved.getCategory(), saved.getSeverity());
        return saved;
    }

    @Transactional
    public void whitelistKeyword(UUID id) {
        SensitiveKeyword kw = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Keyword not found with id: " + id));
        kw.setStatus("WHITELISTED");
        repository.save(kw);
        fastFilter.removeWordFromCache(kw.getKeyword());
        log.info("Whitelisted keyword '{}', removed from active scan cache.", kw.getKeyword());
    }

    @Transactional
    public void deleteKeyword(UUID id) {
        repository.findById(id).ifPresent(kw -> {
            fastFilter.removeWordFromCache(kw.getKeyword());
            repository.delete(kw);
            log.info("Deleted keyword '{}' from system.", kw.getKeyword());
        });
    }

    public Page<SensitiveKeyword> searchKeywords(String keyword, String category, String status, Pageable pageable) {
        return repository.searchKeywords(keyword, category, status, pageable);
    }
}
