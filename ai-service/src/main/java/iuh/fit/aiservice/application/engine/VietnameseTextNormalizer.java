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
        // Phổ biến các biến thể teencode và viết tắt nhạy cảm
        TEENCODE_MAP.put("vcl", "vcl");
        TEENCODE_MAP.put("vkl", "vcl");
        TEENCODE_MAP.put("dcm", "dcm");
        TEENCODE_MAP.put("đcm", "dcm");
        TEENCODE_MAP.put("đkm", "dcm");
        TEENCODE_MAP.put("dkm", "dcm");
        TEENCODE_MAP.put("dm", "dm");
        TEENCODE_MAP.put("đm", "dm");
        TEENCODE_MAP.put("clgt", "clgt");
        TEENCODE_MAP.put("vl", "vl");
        TEENCODE_MAP.put("vlon", "vlon");
        TEENCODE_MAP.put("loz", "lon");
        TEENCODE_MAP.put("lon", "lon");
        TEENCODE_MAP.put("l0n", "lon");
        TEENCODE_MAP.put("djt", "dit");
        TEENCODE_MAP.put("dit", "dit");
        TEENCODE_MAP.put("đjt", "dit");
        TEENCODE_MAP.put("địt", "dit");
        TEENCODE_MAP.put("cặc", "cac");
        TEENCODE_MAP.put("cak", "cac");
        TEENCODE_MAP.put("kac", "cac");
        TEENCODE_MAP.put("buồi", "buoi");
        TEENCODE_MAP.put("đụ", "du");
        TEENCODE_MAP.put("chó", "cho");
        TEENCODE_MAP.put("ngu", "ngu");
        TEENCODE_MAP.put("đĩ", "di");
        TEENCODE_MAP.put("cave", "cave");
        TEENCODE_MAP.put("phò", "pho");
        TEENCODE_MAP.put("thằng lằn", "thang lan");
    }

    /**
     * Chuẩn hóa văn bản:
     * 1. Chuyển Unicode sang NFC
     * 2. Chuyển chữ thường
     * 3. Xóa các ký tự phân tách giả mạo lách luật như: đ.ụ, l_ồ_n, c*ặ*c, d-m
     * 4. Thay thế ký tự số/biểu tượng leetspeak: 0 -> o, 1 -> i, 3 -> e, 4 -> a, @ -> a, ! -> i
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 1. Unicode NFC
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase().trim();

        // 2. Thay thế ký tự Leetspeak phổ biến
        normalized = normalized
                .replace('@', 'a')
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
