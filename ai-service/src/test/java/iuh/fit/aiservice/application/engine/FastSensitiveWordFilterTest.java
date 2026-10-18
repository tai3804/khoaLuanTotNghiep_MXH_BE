package iuh.fit.aiservice.application.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;

class FastSensitiveWordFilterTest {

    private final VietnameseTextNormalizer normalizer = new VietnameseTextNormalizer();
    private FastSensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new FastSensitiveWordFilter(null, normalizer);
        // Một phần từ khóa đang ACTIVE trong DB (hạt giống + từ AI tự học), cùng mức độ như trong DB
        for (String w : new String[]{"buồi", "kèo bóng", "chó đẻ", "mẹ mày", "gái gọi", "lừa đảo", "đánh bạc", "cave", "vcl"}) {
            filter.addOrUpdateWordInCache(w, "PROFANITY", "MEDIUM");
        }
        for (String w : new String[]{"cặc", "lồn", "địt", "đĩ"}) {
            filter.addOrUpdateWordInCache(w, "PROFANITY", "HIGH");
        }
        filter.addOrUpdateWordInCache("đụ", "PROFANITY", "MEDIUM");
        filter.addOrUpdateWordInCache("đáng xấu hổ", "HARASSMENT", "HIGH");
        filter.addOrUpdateWordInCache("địt mẹ", "HARASSMENT", "HIGH");
        filter.addOrUpdateWordInCache("địt mẹ mày", "PROFANITY", "CRITICAL");
        filter.addOrUpdateWordInCache("bắn chết", "PROFANITY", "CRITICAL");
        filter.addOrUpdateWordInCache("khốn nạn", "PROFANITY", "CRITICAL");
    }

    @Test
    void accentedEverydayWordsAreNotFlagged() {
        // 4 câu đầu là bài thật từng bị gắn cờ oan vì "buổi" bị coi là "buồi"
        String[] clean = {
                "Chiều thứ 6 này 17h có buổi tập ở sân trường, mọi người nhớ mang áo đấu",
                "Quán nhỏ trong hẻm đường Hai Bà Trưng, nhạc nhẹ, wifi mạnh, rất hợp ngồi làm việc cả buổi.",
                "Quán cà phê trên đồi chè Cầu Đất: view mây buổi sáng cực đẹp",
                "Cuối tuần này nhóm mình tổ chức buổi chia sẻ về Docker cho người mới",
                "Mình đang xấu hổ quá vì quên mang ví",
                "Mua kẹo bông cho bé nhé",
                "Cho dễ hiểu thì cứ làm theo hướng dẫn",
                "Điểm số học kỳ này của @Điềm khá cao, giáo dục là quan trọng",
                "điểm số, điềm báo, giáo dục, địa điểm", // không được dính "đĩ" / "đụ"
        };
        for (String text : clean) {
            assertThat(filter.scan(text).isToxic()).as(text).isFalse();
        }
    }

    @Test
    void ambiguousWordsTypedWithoutAccentsAreNotEnoughOnTheirOwn() {
        String[] clean = {
                "hop lam viec ca buoi o quan nay",
                "mua keo bong cho be",
                "cho de hieu thi cu lam theo huong dan",
        };
        for (String text : clean) {
            assertThat(filter.scan(text).isToxic()).as(text).isFalse();
        }
    }

    @Test
    void unaccentedMatchIsWeakerEvidenceThanAccentedMatch() {
        FastSensitiveWordFilter.FastScanResult accented = filter.scan("mày thật đáng xấu hổ");
        FastSensitiveWordFilter.FastScanResult bare = filter.scan("may that dang xau ho");
        assertThat(accented.getHighestSeverity()).isEqualTo("HIGH");
        assertThat(bare.getHighestSeverity()).isEqualTo("MEDIUM");
        assertThat(bare.getToxicityScore()).isLessThan(accented.getToxicityScore());
    }

    @Test
    void realProfanityIsStillFlagged() {
        assertThat(filter.scan("đồ buồi").getMatchedKeywords()).contains("buồi");
        assertThat(filter.scan("ĐỊT MẸ MÀY").getHighestSeverity()).isEqualTo("CRITICAL");
        assertThat(filter.scan("mày là đồ khốn nạn").isToxic()).isTrue();
        assertThat(filter.scan("đ.ị.t").isToxic()).isTrue(); // chèn dấu chấm để lách luật
        assertThat(filter.scan("vcl!").isToxic()).isTrue(); // dấu ! cuối câu không được biến "vcl" thành "vcli"
    }

    @Test
    void clearInsultsTypedWithoutAccentsAreStillFlagged() {
        assertThat(filter.scan("dit me may").isToxic()).isTrue();
        assertThat(filter.scan("d1t me m4y").isToxic()).isTrue(); // leetspeak
        assertThat(filter.scan("tao se ban chet may").isToxic()).isTrue();
    }

    @Test
    void cleanTextIsLabelledNone() {
        FastSensitiveWordFilter.FastScanResult result = filter.scan("Hôm nay trời đẹp, mình đi cà phê với bạn");
        assertThat(result.isToxic()).isFalse();
        assertThat(result.getPrimaryCategory()).isEqualTo("NONE");
        assertThat(result.getToxicityScore()).isZero();
    }

    @Test
    void keywordsAddedInDecomposedUnicodeStillMatch() {
        FastSensitiveWordFilter f = new FastSensitiveWordFilter(null, normalizer);
        f.addOrUpdateWordInCache(Normalizer.normalize("vô học", Normalizer.Form.NFD), "PROFANITY", "CRITICAL");
        assertThat(f.scan("đồ vô học").isToxic()).isTrue();
    }

    @Test
    void leetspeakIsOnlyDecodedInsideObfuscatedWords() {
        assertThat(normalizer.normalize("Họp lúc 17h, mang 5kg, năm 2024, giá 3 triệu")).isEqualTo("họp lúc 17h, mang 5kg, năm 2024, giá 3 triệu");
        assertThat(normalizer.normalize("D1T me m4y")).isEqualTo("dit me may");
        assertThat(normalizer.normalize("5ex")).isEqualTo("sex");
        assertThat(normalizer.normalize("Tuyệt vời!")).isEqualTo("tuyệt vời!");
    }
}
