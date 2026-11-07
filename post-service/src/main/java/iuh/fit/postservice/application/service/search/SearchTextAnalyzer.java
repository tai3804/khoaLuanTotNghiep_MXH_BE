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
 * Giai đoạn 0 của pipeline tìm kiếm: hiểu truy vấn (query understanding) và phân tích văn bản.
 *
 * <ul>
 *   <li>{@link #fold}: chuẩn hoá về chữ thường, bỏ dấu tiếng Việt, {@code đ -> d}. Dùng chung cho cả truy vấn lẫn nội dung
 *       bài viết, và <b>khớp hoàn toàn</b> với hàm {@code kltn_fold()} trong PostgreSQL (chỉ mục full-text), nên một từ
 *       luôn ra cùng một token ở cả hai phía.</li>
 *   <li>{@link #tokens}: tách token theo ký tự không phải chữ/số (âm tiết tiếng Việt vốn đã tách bằng khoảng trắng).</li>
 *   <li>{@link #parse}: tách truy vấn thành các từ khoá, hashtag, cụm nguyên văn có dấu và cụm đã bỏ dấu.</li>
 * </ul>
 *
 * Bỏ dấu làm tăng độ phủ (người dùng hay gõ không dấu) nhưng làm mất thông tin ("cả" / "cá"), nên độ chính xác
 * được lấy lại ở các giai đoạn sau: embedding phân biệt ngữ nghĩa và đặc trưng "cụm khớp đúng dấu" được thưởng cao hơn
 * "cụm khớp sau khi bỏ dấu" trong {@link SearchScorer}.
 */
public final class SearchTextAnalyzer {

    /**
     * Từ chức năng tiếng Việt, so khớp theo dạng CÓ DẤU. Không được so sau khi bỏ dấu: "thì" (từ chức năng) và "thi" (kỳ thi)
     * cùng thành "thi", loại nhầm sẽ làm truy vấn "ôn thi" mất chữ quan trọng nhất. Người dùng gõ không dấu ("thi") thì
     * không đoán được ý nên giữ lại; BM25 tự giảm trọng số của từ phổ biến qua idf.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "là", "và", "của", "cho", "với", "những", "các", "một", "thì", "mà", "để", "tại", "trong", "này", "cũng");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final int MAX_TERMS = 12;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MIN_PREFIX_LENGTH = 3;

    private SearchTextAnalyzer() {
    }

    /**
     * @param raw             truy vấn gốc đã cắt khoảng trắng
     * @param exactPhrase     chữ thường (NFC), giữ nguyên dấu; dùng để thưởng khớp đúng dấu
     * @param foldedPhrase    chữ thường, đã bỏ dấu, các token nối bằng một khoảng trắng
     * @param terms           từ khoá duy nhất theo thứ tự xuất hiện, đã bỏ dấu (đã bỏ từ chức năng, tối đa 12)
     * @param exactTerms      dạng có dấu của từng từ khoá (cùng thứ tự); bằng dạng bỏ dấu nếu người dùng gõ không dấu
     * @param hashtags        hashtag trong truy vấn (đã bỏ dấu, không có dấu '#')
     * @param prefixLast      true nếu từ khoá cuối được khớp theo tiền tố (kiểu typeahead khi người dùng chưa gõ xong)
     */
    public record ParsedQuery(String raw, String exactPhrase, String foldedPhrase, List<String> terms,
                              List<String> exactTerms, List<String> hashtags, boolean prefixLast) {
        public boolean isEmpty() {
            return terms.isEmpty();
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
        Matcher m = HASHTAG.matcher(raw);
        while (m.find()) {
            String tag = fold(m.group(1)).replaceAll("[^a-z0-9]", "");
            if (!tag.isEmpty()) hashtags.add(tag);
        }

        List<String> all = tokens(raw);
        // Tách theo dạng có dấu để nhận diện từ chức năng, rồi mới bỏ dấu từng token còn lại.
        // exactTerms[i] là dạng CÓ DẤU của terms[i] (bằng chính terms[i] nếu người dùng gõ không dấu).
        String exactNfc = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> all2 = new ArrayList<>();      // {exact, folded}
        List<String[]> content = new ArrayList<>();
        for (String accented : NON_WORD.split(exactNfc)) {
            if (accented.isEmpty()) continue;
            String folded = fold(accented).replaceAll("[^a-z0-9]", "");
            if (folded.isEmpty() || !seen.add(folded)) continue;
            String[] pair = {accented, folded};
            all2.add(pair);
            if (!STOPWORDS.contains(accented)) content.add(pair);
        }
        List<String[]> chosen = content.isEmpty() ? all2 : content;
        if (chosen.size() > MAX_TERMS) chosen = new ArrayList<>(chosen.subList(0, MAX_TERMS));
        List<String> terms = new ArrayList<>(chosen.size());
        List<String> exactTerms = new ArrayList<>(chosen.size());
        for (String[] p : chosen) {
            terms.add(p[1]);
            exactTerms.add(p[0].equals(p[1]) ? p[1] : p[0]);
        }

        String exact = java.text.Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[#\\s]+", " ").trim();
        String folded = String.join(" ", all);

        // Người dùng đang gõ dở ("lap tri" -> "lap trinh"): khớp tiền tố cho từ cuối nếu truy vấn không kết thúc bằng khoảng trắng/dấu câu.
        // Phải xét chuỗi gốc chưa cắt khoảng trắng: "lap tri " (đã gõ xong) khác "lap tri" (đang gõ).
        String original = rawQuery == null ? "" : rawQuery;
        boolean prefix = !terms.isEmpty() && terms.get(terms.size() - 1).length() >= MIN_PREFIX_LENGTH
                && !original.isEmpty() && Character.isLetterOrDigit(original.charAt(original.length() - 1));

        return new ParsedQuery(raw, exact, folded, List.copyOf(terms), List.copyOf(exactTerms), List.copyOf(hashtags), prefix);
    }

    /** Token giữ nguyên dấu (NFC, chữ thường), dùng để khớp đúng dấu khi người dùng gõ có dấu. */
    public static List<String> exactTokens(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String t : NON_WORD.split(s)) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
