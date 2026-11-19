package iuh.fit.aiservice.application.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AhoCorasickMatcherTest {

    @Test
    void findsOnlyWholeWords() {
        AhoCorasickMatcher m = new AhoCorasickMatcher(List.of("đĩ", "đụ", "buồi", "địt mẹ"));
        assertThat(m.findWholeWords("điểm số, điềm báo, giáo dục, địa điểm")).isEmpty();
        assertThat(m.findWholeWords("buổi sáng")).isEmpty();
        assertThat(m.findWholeWords("đồ buồi")).containsExactly("buồi");
        assertThat(m.findWholeWords("(đĩ)")).containsExactly("đĩ");
    }

    @Test
    void digitsAndLettersAreWordCharacters() {
        AhoCorasickMatcher m = new AhoCorasickMatcher(List.of("sex"));
        assertThat(m.findWholeWords("sex3")).isEmpty();
        assertThat(m.findWholeWords("3sex")).isEmpty();
        assertThat(m.findWholeWords("sexy")).isEmpty();
        assertThat(m.findWholeWords("no sex.")).containsExactly("sex");
    }

    @Test
    void reportsOverlappingAndNestedKeywords() {
        AhoCorasickMatcher m = new AhoCorasickMatcher(List.of("địt", "địt mẹ", "địt mẹ mày", "mẹ mày", "mày"));
        assertThat(m.findWholeWords("địt mẹ mày")).containsExactlyInAnyOrder("địt", "địt mẹ", "địt mẹ mày", "mẹ mày", "mày");
    }

    @Test
    void suffixKeywordIsFoundThroughFailureLinks() {
        // "a b c d" đang khớp dở rồi hỏng ở 'x'; "b c x" phải được tìm thấy nhờ liên kết thất bại
        AhoCorasickMatcher m = new AhoCorasickMatcher(List.of("a b c d", "b c x"));
        assertThat(m.findWholeWords("a b c x")).containsExactly("b c x");
        // và từ khoá đầy đủ vẫn được tìm thấy khi không hỏng giữa chừng
        assertThat(m.findWholeWords("a b c d")).containsExactly("a b c d");
        // "bcx" dính liền sau chữ "a" thì không phải từ độc lập
        assertThat(new AhoCorasickMatcher(List.of("bcx")).findWholeWords("abcx")).isEmpty();
    }

    @Test
    void emptyInputs() {
        assertThat(new AhoCorasickMatcher(List.of()).findWholeWords("bất kỳ")).isEmpty();
        assertThat(new AhoCorasickMatcher(List.of("a")).findWholeWords("")).isEmpty();
        assertThat(new AhoCorasickMatcher(List.of("a")).findWholeWords(null)).isEmpty();
        assertThat(new AhoCorasickMatcher(java.util.Arrays.asList("", null, "ab")).size()).isEqualTo(1);
    }

    /** So sánh với cách cũ (một regex lookaround cho mỗi từ khoá) trên chuỗi ngẫu nhiên: hai cách phải cho cùng tập kết quả. */
    @Test
    void agreesWithRegexReferenceOnRandomInput() {
        String alphabet = "ab đĩ1 .-";
        Random rnd = new Random(42);
        for (int round = 0; round < 300; round++) {
            Set<String> keys = new LinkedHashSet<>();
            int keyCount = 1 + rnd.nextInt(8);
            for (int k = 0; k < keyCount; k++) {
                StringBuilder sb = new StringBuilder();
                int len = 1 + rnd.nextInt(4);
                for (int i = 0; i < len; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                String key = sb.toString().trim();
                if (!key.isEmpty()) keys.add(key);
            }
            StringBuilder text = new StringBuilder();
            int textLen = rnd.nextInt(40);
            for (int i = 0; i < textLen; i++) text.append(alphabet.charAt(rnd.nextInt(alphabet.length())));

            Set<String> expected = new LinkedHashSet<>();
            for (String key : keys) {
                Pattern p = Pattern.compile("(?<![\\p{L}\\p{Nd}])" + Pattern.quote(key) + "(?![\\p{L}\\p{Nd}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
                if (p.matcher(text).find()) expected.add(key);
            }
            Set<String> actual = new AhoCorasickMatcher(new ArrayList<>(keys)).findWholeWords(text.toString());
            assertThat(actual).as("keys=%s text='%s'", keys, text).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
