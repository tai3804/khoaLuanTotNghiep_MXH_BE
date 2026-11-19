package iuh.fit.userservice.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá chữ cho tìm kiếm theo tên: bỏ dấu tiếng Việt, chữ thường, gọn khoảng trắng; kèm cách xếp hạng độ khớp tên.
 * Tìm theo tên là so khớp chữ, không cần mô hình ngôn ngữ, nên đường này không gọi Gemini.
 */
public final class SearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MAX_KEYWORD_CHARS = 100;

    private SearchText() {
    }

    /** "Trần Thành  Tài" -> "tran thanh tai". */
    public static String fold(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFD);
        s = MARKS.matcher(s).replaceAll("").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /**
     * Từ khoá -> mẫu LIKE: bỏ dấu, chữ thường, thoát % _ ! (! là ký tự thoát của LIKE) để người dùng gõ ký tự đặc biệt không
     * thành ký tự đại diện.
     */
    public static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > MAX_KEYWORD_CHARS) k = k.substring(0, MAX_KEYWORD_CHARS);
        k = fold(k);
        k = k.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }

    /**
     * Độ khớp của một tên với từ khoá (đã {@link #fold}); số nhỏ hơn là khớp tốt hơn:
     * 0 trùng cả tên, 1 khớp nguyên từ ("tai" với "tran thanh tai"), 2 đầu một từ ("ta" với "tran thanh tai"), 3 nằm bên trong.
     */
    public static int nameRank(String foldedName, String foldedQuery) {
        if (foldedQuery.isEmpty()) return 3;
        if (foldedName.equals(foldedQuery)) return 0;
        String padded = " " + foldedName + " ";
        if (padded.contains(" " + foldedQuery + " ")) return 1;
        if (padded.contains(" " + foldedQuery)) return 2;
        return 3;
    }
}
