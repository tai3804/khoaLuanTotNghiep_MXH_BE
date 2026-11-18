package iuh.fit.postservice.application.service.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hiểu truy vấn (query understanding) cho tìm kiếm ngữ nghĩa. Không còn tách từ khoá cho chỉ mục toàn văn: truy vấn được đưa
 * thẳng vào mô hình embedding, nên ở đây chỉ cần những gì phục vụ xếp hạng lại:
 *
 * <ul>
 *   <li>{@link #fold}: chữ thường, bỏ dấu tiếng Việt, {@code đ -> d};</li>
 *   <li>{@link #parse}: cụm nguyên văn có dấu (thưởng khớp đúng dấu), cụm đã bỏ dấu, và các hashtag trong truy vấn.</li>
 * </ul>
 *
 * Bỏ dấu làm tăng độ phủ (người dùng hay gõ không dấu) nhưng làm mất thông tin ("cả" / "cá"); độ chính xác được lấy lại nhờ
 * embedding phân biệt ngữ nghĩa và nhờ đặc trưng "cụm khớp đúng dấu" được thưởng cao hơn "cụm khớp sau khi bỏ dấu".
 */
public final class SearchTextAnalyzer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final int MAX_QUERY_LENGTH = 200;

    private SearchTextAnalyzer() {
    }

    /**
     * @param raw          truy vấn gốc đã cắt khoảng trắng (tối đa 200 ký tự)
     * @param exactPhrase  chữ thường (NFC), giữ nguyên dấu, bỏ '#'; dùng để thưởng khớp đúng dấu
     * @param foldedPhrase chữ thường, đã bỏ dấu, các token nối bằng một khoảng trắng
     * @param hashtags     hashtag trong truy vấn dạng đã bỏ dấu và chỉ gồm a-z0-9 (để so với hashtag của bài)
     * @param hashtagTags  hashtag trong truy vấn dạng chữ thường có dấu, đúng như được lưu trong CSDL (để truy xuất)
     */
    public record ParsedQuery(String raw, String exactPhrase, String foldedPhrase,
                              List<String> hashtags, List<String> hashtagTags) {
        public boolean isEmpty() {
            return raw.isEmpty() || (foldedPhrase.isEmpty() && hashtags.isEmpty());
        }
    }

    public static String fold(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.replace('đ', 'd');
    }

    public static List<String> tokens(String text) {
        String folded = fold(text);
        List<String> out = new ArrayList<>();
        for (String t : NON_ALNUM.split(folded)) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static ParsedQuery parse(String rawQuery) {
        String raw = rawQuery == null ? "" : rawQuery.trim();
        if (raw.length() > MAX_QUERY_LENGTH) raw = raw.substring(0, MAX_QUERY_LENGTH);

        Set<String> hashtags = new LinkedHashSet<>();
        Set<String> hashtagTags = new LinkedHashSet<>();
        Matcher m = HASHTAG.matcher(raw);
        while (m.find()) {
            String tag = fold(m.group(1)).replaceAll("[^a-z0-9]", "");
            if (!tag.isEmpty()) hashtags.add(tag);
            hashtagTags.add(Normalizer.normalize(m.group(1), Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
        }

        String exact = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[#\\s]+", " ").trim();
        String folded = String.join(" ", tokens(raw));
        return new ParsedQuery(raw, exact, folded, List.copyOf(hashtags), List.copyOf(hashtagTags));
    }
}
