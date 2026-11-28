package iuh.fit.aiservice.application.engine;

import iuh.fit.aiservice.infrastructure.feign.AdminFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Từ cấm do quản trị viên thêm ở trang "Từ điển từ cấm" (admin_db). Đây là luật cứng: bài viết chứa nguyên một từ trong danh sách
 * (đúng ranh giới chữ/số, không phân biệt hoa thường, kể cả khi bị chèn dấu chấm/gạch để lách) thì bị tạm ẩn, không cần hỏi Gemini.
 * Danh sách được đọc lại từ admin-service tối đa mỗi {@link #REFRESH_MS} ms; admin-service lỗi thì giữ danh sách đã có.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBlacklistMatcher {

    static final long REFRESH_MS = 10_000;

    private final AdminFeignClient adminFeignClient;
    private final VietnameseTextNormalizer normalizer;

    /** Dạng chuẩn hoá -> từ gốc quản trị viên nhập; cùng automaton dựng từ các dạng đó. */
    private record Dictionary(Map<String, String> originals, AhoCorasickMatcher matcher) {
    }

    private volatile Dictionary dictionary = new Dictionary(Map.of(), new AhoCorasickMatcher(List.of()));
    private volatile long lastRefresh = 0;

    /** Các từ cấm (dạng quản trị viên nhập) xuất hiện trong văn bản; rỗng nếu không có. */
    public List<String> findMatches(String text) {
        if (text == null || text.isBlank()) return List.of();
        refreshIfStale();
        Dictionary dict = dictionary;
        if (dict.originals().isEmpty()) return List.of();

        String lower = canonical(text);
        String normalized = normalizer.normalize(text);
        Set<String> hits = new LinkedHashSet<>(dict.matcher().findWholeWords(lower));
        hits.addAll(dict.matcher().findWholeWords(normalized));
        hits.addAll(dict.matcher().findWholeWords(normalizer.removeObfuscation(normalized)));

        Set<String> words = new LinkedHashSet<>();
        for (String hit : hits) words.add(dict.originals().get(hit));
        return new ArrayList<>(words);
    }

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_MS) return;
        synchronized (this) {
            if (now - lastRefresh < REFRESH_MS) return;
            lastRefresh = now;
            try {
                List<String> words = adminFeignClient.getBlacklistedWords();
                dictionary = build(words != null ? words : List.of());
            } catch (Exception e) {
                log.debug("Could not refresh admin blacklist, keeping {} cached words: {}", dictionary.originals().size(), e.getMessage());
            }
        }
    }

    /** Dựng từ điển từ danh sách từ; tách riêng để kiểm thử không cần admin-service. */
    Dictionary build(Collection<String> words) {
        Map<String, String> originals = new HashMap<>();
        for (String raw : words) {
            if (raw == null || raw.isBlank()) continue;
            String word = canonical(raw);
            originals.putIfAbsent(word, word);
            // Cùng dạng chuẩn hoá với văn bản (giải leetspeak) để "c4c" của quản trị viên vẫn khớp với văn bản đã chuẩn hoá
            String normalized = normalizer.normalize(word);
            if (!normalized.isEmpty()) originals.putIfAbsent(normalized, word);
        }
        return new Dictionary(originals, new AhoCorasickMatcher(originals.keySet()));
    }

    void replaceForTest(Collection<String> words) {
        dictionary = build(words);
        lastRefresh = System.currentTimeMillis();
    }

    private static String canonical(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase().trim();
    }
}
