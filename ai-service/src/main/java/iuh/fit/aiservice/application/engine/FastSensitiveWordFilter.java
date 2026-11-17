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

@Slf4j
@Component
@RequiredArgsConstructor
public class FastSensitiveWordFilter {

    private final SensitiveKeywordRepository sensitiveKeywordRepository;
    private final VietnameseTextNormalizer normalizer;

    /** Mức độ và nhóm của một từ khóa trong từ điển. */
    private record Entry(String severity, String category) {
    }

    /**
     * Bản chụp đã dựng của từ điển: hai automaton Aho–Corasick (dạng có dấu và dạng không dấu) cùng bảng tra ngược.
     * Bất biến, được thay thế nguyên khối nên luồng đang quét không bao giờ thấy từ điển "đang nạp dở".
     */
    private record Snapshot(AhoCorasickMatcher exact, AhoCorasickMatcher unaccented,
                            Map<String, List<String>> wordsByUnaccented, Map<String, Entry> entries) {
    }

    // Từ điển nguồn (từ khóa chuẩn hóa -> mức độ, nhóm). reloadCache thay nguyên khối nên không có khoảng hở rỗng;
    // thêm/xóa lẻ chỉ đánh dấu dirty, automaton được dựng lại một lần ở lần quét kế tiếp (gộp được cả loạt thay đổi).
    private volatile Map<String, Entry> dictionary = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot;
    private volatile boolean dirty = true;
    private final Object rebuildLock = new Object();

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
                dictionary.put(canonical(seed), new Entry("MEDIUM", "PROFANITY"));
            }
            dirty = true;
        }
    }

    public synchronized void reloadCache() {
        List<SensitiveKeyword> keywords = sensitiveKeywordRepository.findByStatus("ACTIVE");

        // Dựng từ điển mới ở bên ngoài rồi hoán đổi một lần. Trước đây clear() rồi nạp lại từng từ trên chính các bản đồ
        // đang được quét, nên có khoảng thời gian từ điển rỗng khiến nội dung độc hại lọt qua tầng 1.
        Map<String, Entry> fresh = new ConcurrentHashMap<>();
        for (SensitiveKeyword kw : keywords) {
            String word = canonical(kw.getKeyword());
            if (word.isEmpty()) continue;
            fresh.put(word, new Entry(
                    kw.getSeverity() != null ? kw.getSeverity() : "MEDIUM",
                    kw.getCategory() != null ? kw.getCategory() : "PROFANITY"));
        }
        dictionary = fresh;
        dirty = true;
        log.info("Loaded {} active sensitive keywords into FastSensitiveWordFilter cache.", fresh.size());
    }

    public void addOrUpdateWordInCache(String keyword, String category, String severity) {
        String word = canonical(keyword);
        if (word.isEmpty()) return;
        dictionary.put(word, new Entry(severity != null ? severity : "MEDIUM", category != null ? category : "PROFANITY"));
        dirty = true;
    }

    public void removeWordFromCache(String keyword) {
        dictionary.remove(canonical(keyword));
        dirty = true;
    }

    /** Số từ khóa đang hiệu lực trong bộ nhớ. */
    public int activeWordCount() {
        return dictionary.size();
    }

    /** Bản chụp hiện hành; dựng lại automaton nếu từ điển vừa thay đổi (chỉ một luồng dựng, các luồng khác chờ rồi dùng chung). */
    private Snapshot currentSnapshot() {
        Snapshot s = snapshot;
        if (s != null && !dirty) return s;
        synchronized (rebuildLock) {
            if (snapshot == null || dirty) {
                // Hạ cờ TRƯỚC khi đọc từ điển: thay đổi xảy ra giữa chừng sẽ đặt lại cờ và lần quét sau dựng lại.
                dirty = false;
                snapshot = buildSnapshot(new HashMap<>(dictionary));
            }
            return snapshot;
        }
    }

    private Snapshot buildSnapshot(Map<String, Entry> entries) {
        Map<String, List<String>> byUnaccented = new HashMap<>();
        for (String word : entries.keySet()) {
            String noDia = normalizer.removeDiacritics(word);
            // Chỉ dùng dạng không dấu khi nó khác dạng gốc và đủ dài (xem MIN_UNACCENTED_LENGTH)
            if (!noDia.equals(word) && noDia.length() >= MIN_UNACCENTED_LENGTH) {
                byUnaccented.computeIfAbsent(noDia, k -> new ArrayList<>()).add(word);
            }
        }
        return new Snapshot(new AhoCorasickMatcher(entries.keySet()), new AhoCorasickMatcher(byUnaccented.keySet()),
                byUnaccented, entries);
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
        Snapshot dict = currentSnapshot();
        Map<String, String> matched = new LinkedHashMap<>();

        // Bằng chứng mạnh: khớp nguyên từ, đúng từng dấu thanh/dấu mũ. "buổi" khác "buồi" nên không khớp nhau.
        // Mỗi văn bản chỉ cần MỘT lượt quét Aho–Corasick cho toàn bộ từ điển.
        Set<String> strong = new LinkedHashSet<>(dict.exact().findWholeWords(normalized));
        strong.addAll(dict.exact().findWholeWords(deObfuscated));
        for (String word : strong) {
            matched.put(word, normalizeSeverity(severityOf(dict, word)));
        }

        // Bằng chứng yếu: người viết gõ không dấu và đúng dạng không dấu của từ khóa. Không được bỏ dấu cả bài viết rồi so
        // sánh, vì sẽ bắt nhầm "buổi" thành "buồi", "đang xấu hổ" thành "đáng xấu hổ", "kẹo bông" thành "kèo bóng".
        for (String noDia : dict.unaccented().findWholeWords(deObfuscated)) {
            for (String word : dict.wordsByUnaccented().getOrDefault(noDia, List.of())) {
                if (matched.containsKey(word)) continue;
                String weakened = weaken(normalizeSeverity(severityOf(dict, word)));
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
                Entry entry = dict.entries().get(m.getKey());
                primaryCategory = entry != null && entry.category() != null ? entry.category() : "PROFANITY";
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

    private static String severityOf(Snapshot dict, String word) {
        Entry entry = dict.entries().get(word);
        return entry != null && entry.severity() != null ? entry.severity() : "MEDIUM";
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
