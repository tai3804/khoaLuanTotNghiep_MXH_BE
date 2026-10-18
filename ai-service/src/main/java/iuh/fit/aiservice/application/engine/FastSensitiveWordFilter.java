package iuh.fit.aiservice.application.engine;

import iuh.fit.aiservice.domain.entities.SensitiveKeyword;
import iuh.fit.aiservice.domain.repository.SensitiveKeywordRepository;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
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

    // Từ khóa không dấu ngắn hơn mức này quá mơ hồ ("cac" = "các", "pho" = "phở", "lon" = "lon bia", "du" = "du lịch")
    private static final int MIN_UNACCENTED_LENGTH = 4;

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
                activeWords.add(canonical(seed));
                wordSeverityMap.put(canonical(seed), "MEDIUM");
                wordCategoryMap.put(canonical(seed), "PROFANITY");
            }
        }
    }

    public synchronized void reloadCache() {
        List<SensitiveKeyword> keywords = sensitiveKeywordRepository.findByStatus("ACTIVE");
        activeWords.clear();
        wordSeverityMap.clear();
        wordCategoryMap.clear();

        for (SensitiveKeyword kw : keywords) {
            String word = canonical(kw.getKeyword());
            activeWords.add(word);
            wordSeverityMap.put(word, kw.getSeverity() != null ? kw.getSeverity() : "MEDIUM");
            wordCategoryMap.put(word, kw.getCategory() != null ? kw.getCategory() : "PROFANITY");
        }
        log.info("Loaded {} active sensitive keywords into FastSensitiveWordFilter cache.", activeWords.size());
    }

    public void addOrUpdateWordInCache(String keyword, String category, String severity) {
        String word = canonical(keyword);
        activeWords.add(word);
        wordCategoryMap.put(word, category != null ? category : "PROFANITY");
        wordSeverityMap.put(word, severity != null ? severity : "MEDIUM");
    }

    public void removeWordFromCache(String keyword) {
        String word = canonical(keyword);
        activeWords.remove(word);
        wordCategoryMap.remove(word);
        wordSeverityMap.remove(word);
    }

    /**
     * Quét nhanh văn bản qua các lớp chuẩn hóa: Gốc và Xóa ký tự lách luật
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

        // Loại bỏ các tag mention @[Tên] hoặc @Tên khỏi chuỗi kiểm duyệt từ khóa thô để tránh chặn tên bạn bè
        String textWithoutMentions = rawText
                .replaceAll("@\\[[^\\]]+\\](?:\\([a-zA-Z0-9_\\-]+\\))?", " ")
                .replaceAll("@[\\p{L}\\p{N}_]+", " ");

        String normalized = normalizer.normalize(textWithoutMentions);
        String deObfuscated = normalizer.removeObfuscation(normalized);

        // Từ khóa khớp -> mức độ hiệu lực của lần khớp đó (xem weaken)
        Map<String, String> matched = new LinkedHashMap<>();
        for (String word : activeWords) {
            if (word.isEmpty()) {
                continue;
            }
            String severity = normalizeSeverity(wordSeverityMap.getOrDefault(word, "MEDIUM"));

            // Bằng chứng mạnh: khớp nguyên từ, đúng từng dấu thanh/dấu mũ. "buổi" khác "buồi" nên không khớp nhau.
            if (containsExactWord(normalized, word) || containsExactWord(deObfuscated, word)) {
                matched.put(word, severity);
                continue;
            }

            // Bằng chứng yếu: người viết gõ không dấu và đúng dạng không dấu của từ khóa. Không được bỏ dấu cả bài viết rồi so
            // sánh, vì sẽ bắt nhầm "buổi" thành "buồi", "đang xấu hổ" thành "đáng xấu hổ", "kẹo bông" thành "kèo bóng".
            String wordNoDia = normalizer.removeDiacritics(word);
            if (!wordNoDia.equals(word) && wordNoDia.length() >= MIN_UNACCENTED_LENGTH && containsExactWord(deObfuscated, wordNoDia)) {
                String weakened = weaken(severity);
                if (weakened != null) {
                    matched.put(word, weakened);
                }
            }
        }

        String highestSeverity = "LOW";
        String primaryCategory = "NONE"; // bài sạch không được mang nhãn PROFANITY
        int highestRank = -1;
        int criticalCount = 0;
        int highCount = 0;
        int mediumCount = 0;
        for (Map.Entry<String, String> m : matched.entrySet()) {
            String severity = m.getValue();
            if (severityRank(severity) > highestRank) {
                highestRank = severityRank(severity);
                highestSeverity = severity;
                primaryCategory = wordCategoryMap.getOrDefault(m.getKey(), "PROFANITY");
            }
            switch (severity) {
                case "CRITICAL" -> criticalCount++;
                case "HIGH" -> highCount++;
                case "MEDIUM" -> mediumCount++;
                default -> { }
            }
        }

        double score = 0.0;
        if (criticalCount > 0) {
            score = Math.min(1.0, 0.85 + (criticalCount * 0.05));
        } else if (highCount > 0) {
            score = Math.min(0.84, 0.65 + (highCount * 0.05));
        } else if (mediumCount > 0) {
            score = Math.min(0.64, 0.40 + (mediumCount * 0.05));
        }

        return FastScanResult.builder()
                .isToxic(!matched.isEmpty())
                .toxicityScore(score)
                .matchedKeywords(new ArrayList<>(matched.keySet()))
                .highestSeverity(highestSeverity)
                .primaryCategory(primaryCategory)
                .build();
    }

    /**
     * Chữ không dấu là bằng chứng yếu hơn: "buoi" có thể là "buổi", "buồi" hay "buội". Mỗi lần khớp không dấu bị hạ một bậc
     * (CRITICAL -> HIGH -> MEDIUM), còn MEDIUM thì không đủ để tự nó kết luận gì nên bỏ qua (trả về null).
     * Lời lăng mạ rõ ràng gõ không dấu vẫn bị bắt vì thường chứa từ khóa CRITICAL/HIGH hoặc nhiều từ khóa cùng lúc.
     */
    private static String weaken(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "HIGH";
            case "HIGH" -> "MEDIUM";
            default -> null;
        };
    }

    private static String normalizeSeverity(String severity) {
        String upper = severity == null ? "" : severity.trim().toUpperCase();
        return severityRank(upper) > 0 ? upper : "LOW";
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    /** Từ khóa luôn ở dạng NFC chữ thường để khớp với văn bản đã chuẩn hóa, kể cả khi quản trị viên gõ dấu kiểu tổ hợp. */
    private static String canonical(String keyword) {
        return Normalizer.normalize(keyword, Normalizer.Form.NFC).toLowerCase().trim();
    }

    /**
     * Kiểm tra khớp từ độc lập theo ranh giới từ Unicode (Lookaround boundary)
     * Tránh lỗi substring matching (ví dụ "điềm", "điểm" bị dính "đĩ", hay "giáo dục" bị dính "đụ").
     */
    private boolean containsExactWord(String text, String word) {
        if (text == null || word == null || word.trim().isEmpty()) return false;
        try {
            String escapedWord = Pattern.quote(word.trim());
            Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{Nd}])" + escapedWord + "(?![\\p{L}\\p{Nd}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            return pattern.matcher(text).find();
        } catch (Exception e) {
            return false;
        }
    }

    private String determineDefaultSeverity(String word) {
        if (word.contains("giết") || word.contains("bắn") || word.contains("sex") || word.contains("khiêu dâm")) {
            return "CRITICAL";
        }
        return "HIGH";
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
