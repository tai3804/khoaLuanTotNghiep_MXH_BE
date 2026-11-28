package iuh.fit.aiservice.application.engine;

import iuh.fit.aiservice.infrastructure.feign.AdminFeignClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBlacklistMatcherTest {

    private final VietnameseTextNormalizer normalizer = new VietnameseTextNormalizer();

    private AdminBlacklistMatcher matcherWith(List<String> words) {
        AdminBlacklistMatcher m = new AdminBlacklistMatcher(null, normalizer);
        m.replaceForTest(words);
        return m;
    }

    @Test
    void numericWordIsMatchedAsWholeWord() {
        AdminBlacklistMatcher m = matcherWith(List.of("123"));
        assertThat(m.findMatches("123")).containsExactly("123");
        assertThat(m.findMatches("Mã của mình là 123 nhé")).containsExactly("123");
        assertThat(m.findMatches("1.2.3")).containsExactly("123");
        // Một phần của số/chữ khác thì không tính
        assertThat(m.findMatches("Số 1234 và abc123")).isEmpty();
    }

    @Test
    void caseAndDiacriticFormsMatch() {
        AdminBlacklistMatcher m = matcherWith(List.of("lừa đảo", "Scam"));
        assertThat(m.findMatches("Trang này LỪA ĐẢO đấy")).containsExactly("lừa đảo");
        assertThat(m.findMatches("đây là s.c.a.m")).containsExactly("scam");
        assertThat(m.findMatches("Hôm nay trời đẹp")).isEmpty();
    }

    @Test
    void refreshesFromAdminServiceAndKeepsListWhenItFails() {
        AtomicInteger calls = new AtomicInteger();
        AdminFeignClient client = new AdminFeignClient() {
            @Override
            public void learnSensitiveWord(String word) { }

            @Override
            public List<String> getBlacklistedWords() {
                if (calls.incrementAndGet() > 1) throw new RuntimeException("admin-service down");
                return List.of("123");
            }

            @Override
            public Map<String, String> getSystemConfigs() { return Map.of(); }
        };
        AdminBlacklistMatcher m = new AdminBlacklistMatcher(client, normalizer);
        assertThat(m.findMatches("123")).containsExactly("123");
        // Lần đọc trong 10 giây tiếp theo dùng lại danh sách, không gọi admin-service
        assertThat(m.findMatches("abc 123")).containsExactly("123");
        assertThat(calls.get()).isEqualTo(1);
    }
}
