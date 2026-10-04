package iuh.fit.aiservice.application.engine;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class VietnameseTextNormalizer {

    private static final Map<String, String> TEENCODE_MAP = new HashMap<>();

    static {
        // Phổ biến các biến thể teencode và viết tắt nhạy cảm rõ ràng (tránh các từ 2 chữ cái thông dụng)
        TEENCODE_MAP.put("vcl", "vcl");
        TEENCODE_MAP.put("vkl", "vcl");
        TEENCODE_MAP.put("dcm", "dcm");
        TEENCODE_MAP.put("đcm", "dcm");
        TEENCODE_MAP.put("đkm", "dcm");
        TEENCODE_MAP.put("dkm", "dcm");
        TEENCODE_MAP.put("clgt", "clgt");
        TEENCODE_MAP.put("vlon", "vlon");
        TEENCODE_MAP.put("loz", "lon");
        TEENCODE_MAP.put("l0n", "lon");
        TEENCODE_MAP.put("djt", "dit");
        TEENCODE_MAP.put("đjt", "dit");
        TEENCODE_MAP.put("cak", "cac");
        TEENCODE_MAP.put("kac", "cac");
        TEENCODE_MAP.put("buồi", "buoi");
        TEENCODE_MAP.put("cave", "cave");
    }

    /**
     * Chuẩn hóa văn bản:
     * 1. Chuyển Unicode sang NFC
     * 2. Chuyển chữ thường
     * 3. Thay thế ký tự số/biểu tượng leetspeak khi nằm trong từ (0->o, 1->i, 3->e, 4->a, 5->s, 7->t, $->s)
     * (KHÔNG chuyển @ thành 'a' để tránh làm hỏng các tag @Tên người dùng)
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 1. Unicode NFC
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase().trim();

        // 2. Thay thế ký tự Leetspeak số
        normalized = normalized
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('!', 'i')
                .replace('$', 's');

        return normalized;
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
     * Chuyển chuỗi tiếng Việt có dấu thành không dấu để quét đối sánh mở rộng
     */
    public String removeDiacritics(String text) {
        if (text == null) return "";
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}
