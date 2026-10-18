package iuh.fit.aiservice.application.engine;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VietnameseTextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern WORD_TOKEN = Pattern.compile("[\\p{L}\\p{Nd}!$]+");
    /** Số kèm đơn vị đo ("17h", "5kg", "100ms", "8gb") là số liệu bình thường, không phải chữ bị viết biến dạng. */
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "\\d+(?:h|m|k|g|kg|km|cm|mm|ml|l|s|ms|gb|mb|kb|tb|hz|khz|mhz|ghz|w|kw|v|a|p|d|x|tr|px|fps|dpi)");

    /**
     * Chuẩn hóa văn bản:
     * 1. Chuyển Unicode sang NFC (để "ồ" gõ kiểu tổ hợp hay dựng sẵn đều giống nhau)
     * 2. Chuyển chữ thường
     * 3. Giải mã leetspeak (0->o, 1->i, 3->e, 4->a, 5->s, 7->t, !->i, $->s) nhưng chỉ trong những từ thật sự bị biến dạng,
     * không đụng tới số và đơn vị bình thường như "17h", "5kg", "2024", và không đụng tới dấu "!" cuối câu ("vcl!").
     * (KHÔNG chuyển @ thành 'a' để tránh làm hỏng các tag @Tên người dùng)
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase().trim();
        return decodeLeetspeak(normalized);
    }

    /**
     * Loại bỏ các dấu câu/ký tự đặc biệt xen giữa các chữ cái nhằm lách luật
     */
    public String removeObfuscation(String text) {
        if (text == null) return "";
        // Biến: "đ.ị.t" hoặc "đ_ị_t" hoặc "đ-ị-t" thành "địt"
        return text.replaceAll("(?<=[\\p{L}\\d])[._\\-*+=~`^!#%&|/\\\\]+(?=[\\p{L}\\d])", "");
    }

    /**
     * Bỏ dấu của một chuỗi (dùng để so với từ khóa viết không dấu).
     * Không được dùng để bỏ dấu cả bài viết rồi so sánh: "buổi" và "buồi" là hai từ khác nhau, chỉ khác dấu.
     */
    public String removeDiacritics(String text) {
        if (text == null) return "";
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private String decodeLeetspeak(String text) {
        Matcher matcher = WORD_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(decodeToken(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Một từ chỉ bị coi là biến dạng khi có ít nhất 2 chữ cái; số và ký hiệu phải dính liền với chữ cái. */
    private String decodeToken(String token) {
        if (token.codePoints().filter(Character::isLetter).count() < 2 || NUMBER_WITH_UNIT.matcher(token).matches()) {
            return token;
        }
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean afterLetter = i > 0 && Character.isLetter(token.charAt(i - 1));
            boolean beforeLetter = i < token.length() - 1 && Character.isLetter(token.charAt(i + 1));
            sb.append(switch (c) {
                case '0' -> afterLetter || beforeLetter ? 'o' : c;
                case '1' -> afterLetter || beforeLetter ? 'i' : c;
                case '3' -> afterLetter || beforeLetter ? 'e' : c;
                case '4' -> afterLetter || beforeLetter ? 'a' : c;
                case '5' -> afterLetter || beforeLetter ? 's' : c;
                case '7' -> afterLetter || beforeLetter ? 't' : c;
                case '!' -> afterLetter && beforeLetter ? 'i' : c;
                case '$' -> afterLetter && beforeLetter ? 's' : c;
                default -> c;
            });
        }
        return sb.toString();
    }
}
