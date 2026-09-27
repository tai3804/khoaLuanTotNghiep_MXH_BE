package iuh.fit.aiservice.application.engine;

import iuh.fit.aiservice.domain.entities.SensitiveKeyword;
import iuh.fit.aiservice.domain.repository.SensitiveKeywordRepository;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastSensitiveWordFilter {

    private final SensitiveKeywordRepository sensitiveKeywordRepository;
    private final VietnameseTextNormalizer normalizer;

    // Cache các từ khóa nhạy cảm trong bộ nhớ
    private final Set<String> activeWords = ConcurrentHashMap.newKeySet();
    private final Map<String, String> wordSeverityMap = new ConcurrentHashMap<>();
    private final Map<String, String> wordCategoryMap = new ConcurrentHashMap<>();

    // Tập từ khóa hạt giống khởi tạo ban đầu
    private static final List<String> SEED_PROFANITIES = Arrays.asList(
            "địt", "djt", "dcm", "đcm", "đkm", "dkm", "vcl", "vkl", "clgt", "cặc", "cak",
            "buồi", "lồn", "loz", "đụ", "đĩ", "cave", "phò", "chó đẻ", "mẹ mày", "óc chó",
            "chết tiệt", "giết người", "bắn chết", "lừa đảo", "đánh bạc", "kèo bóng",
            "sex", "gái gọi", "khiêu dâm"
    );

    @PostConstruct
    public void initDictionary() {
        log.info("Initializing FastSensitiveWordFilter dictionary...");
        try {
            // Nạp từ hạt giống nếu DB trống
            if (sensitiveKeywordRepository.count() == 0) {
                log.info("Bootstrapping initial sensitive words into database...");
                for (String seed : SEED_PROFANITIES) {
                    SensitiveKeyword keyword = SensitiveKeyword.builder()
                            .keyword(seed.toLowerCase().trim())
                            .category("PROFANITY")
                            .severity(determineDefaultSeverity(seed))
                            .hitCount(0)
                            .isAutoLearned(false)
                            .status("ACTIVE")
                            .confidenceScore(0.95)
                            .build();
                    sensitiveKeywordRepository.save(keyword);
                }
            }

            // Nạp toàn bộ từ ACTIVE vào cache
            reloadCache();
        } catch (Exception e) {
            log.error("Failed to initialize sensitive word dictionary: {}", e.getMessage());
            // Fallback nạp danh sách hạt giống vào cache
            for (String seed : SEED_PROFANITIES) {
                activeWords.add(seed.toLowerCase());
                wordSeverityMap.put(seed.toLowerCase(), "MEDIUM");
                wordCategoryMap.put(seed.toLowerCase(), "PROFANITY");
            }
        }
    }

    public synchronized void reloadCache() {
        List<SensitiveKeyword> keywords = sensitiveKeywordRepository.findByStatus("ACTIVE");
        activeWords.clear();
        wordSeverityMap.clear();
        wordCategoryMap.clear();

        for (SensitiveKeyword kw : keywords) {
            String word = kw.getKeyword().toLowerCase().trim();
            activeWords.add(word);
            wordSeverityMap.put(word, kw.getSeverity() != null ? kw.getSeverity() : "MEDIUM");
            wordCategoryMap.put(word, kw.getCategory() != null ? kw.getCategory() : "PROFANITY");
        }
        log.info("Loaded {} active sensitive keywords into FastSensitiveWordFilter cache.", activeWords.size());
    }

    public void addOrUpdateWordInCache(String keyword, String category, String severity) {
        String word = keyword.toLowerCase().trim();
        activeWords.add(word);
        wordCategoryMap.put(word, category != null ? category : "PROFANITY");
        wordSeverityMap.put(word, severity != null ? severity : "MEDIUM");
    }

    public void removeWordFromCache(String keyword) {
        String word = keyword.toLowerCase().trim();
        activeWords.remove(word);
        wordCategoryMap.remove(word);
        wordSeverityMap.remove(word);
    }

    /**
     * Quét nhanh văn bản qua 3 lớp chuẩn hóa: Gốc, Không dấu, và Xóa ký tự lách luật
     */
    public FastScanResult scan(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return FastScanResult.builder()
                    .isToxic(false)
                    .toxicityScore(0.0)
                    .matchedKeywords(Collections.emptyList())
                    .highestSeverity("LOW")
                    .primaryCategory("NONE")
                    .build();
        }

        String normalized = normalizer.normalize(rawText);
        String deObfuscated = normalizer.removeObfuscation(normalized);
        String noDiacritics = normalizer.removeDiacritics(deObfuscated);

        Set<String> matched = new LinkedHashSet<>();
        String highestSeverity = "LOW";
        String primaryCategory = "PROFANITY";
        int criticalCount = 0;
        int highCount = 0;
        int mediumCount = 0;

        for (String word : activeWords) {
            String wordNoDia = normalizer.removeDiacritics(word);

            // Kiểm tra khớp từ nguyên vẹn hoặc từ trong văn bản
            if (containsWord(normalized, word) || containsWord(deObfuscated, word) || containsWord(noDiacritics, wordNoDia)) {
                matched.add(word);
                String severity = wordSeverityMap.getOrDefault(word, "MEDIUM");
                String category = wordCategoryMap.getOrDefault(word, "PROFANITY");

                if ("CRITICAL".equalsIgnoreCase(severity)) {
                    criticalCount++;
                    highestSeverity = "CRITICAL";
                    primaryCategory = category;
                } else if ("HIGH".equalsIgnoreCase(severity) && !"CRITICAL".equalsIgnoreCase(highestSeverity)) {
                    highCount++;
                    highestSeverity = "HIGH";
                    primaryCategory = category;
                } else if ("MEDIUM".equalsIgnoreCase(severity) && "LOW".equalsIgnoreCase(highestSeverity)) {
                    mediumCount++;
                    highestSeverity = "MEDIUM";
                    primaryCategory = category;
                }
            }
        }

        boolean isToxic = !matched.isEmpty();
        double score = 0.0;
        if (criticalCount > 0) {
            score = Math.min(1.0, 0.85 + (criticalCount * 0.05));
        } else if (highCount > 0) {
            score = Math.min(0.84, 0.65 + (highCount * 0.05));
        } else if (mediumCount > 0) {
            score = Math.min(0.64, 0.40 + (mediumCount * 0.05));
        }

        return FastScanResult.builder()
                .isToxic(isToxic)
                .toxicityScore(score)
                .matchedKeywords(new ArrayList<>(matched))
                .highestSeverity(highestSeverity)
                .primaryCategory(primaryCategory)
                .build();
    }

    private boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        // Kiểm tra chứa chính xác từ hoặc ranh giới từ
        return text.contains(word) || Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }

    private String determineDefaultSeverity(String word) {
        if (word.contains("giết") || word.contains("bắn") || word.contains("sex") || word.contains("khiêu dâm")) {
            return "CRITICAL";
        }
        if (word.contains("địt") || word.contains("cặc") || word.contains("lồn") || word.contains("đĩ")) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    @Data
    @Builder
    public static class FastScanResult {
        private boolean isToxic;
        private double toxicityScore;
        private List<String> matchedKeywords;
        private String highestSeverity;
        private String primaryCategory;
    }
}
