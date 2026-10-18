package iuh.fit.postservice.infrastructure.persistence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Tìm bài viết không phân biệt dấu ("ca phe" tìm được "Cà phê"): bật extension unaccent của PostgreSQL lúc khởi động.
 * Nếu không bật được (không đủ quyền) thì tìm kiếm quay về kiểu phân biệt dấu thay vì lỗi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostSearchSupport implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean unaccentAvailable = false;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            Integer found = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'", Integer.class);
            unaccentAvailable = found != null && found > 0;
            log.info("Accent-insensitive post search is {}", unaccentAvailable ? "enabled" : "disabled");
        } catch (Exception e) {
            unaccentAvailable = false;
            log.warn("Could not enable the unaccent extension, post search stays accent-sensitive: {}", e.getMessage());
        }
    }

    public boolean isUnaccentAvailable() {
        return unaccentAvailable;
    }

    /** Từ khoá → mẫu LIKE (ký tự thoát là '!'): chữ thường, bỏ dấu nếu DB hỗ trợ, thoát % _ ! do người dùng gõ. */
    public String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > 100) k = k.substring(0, 100);
        k = k.toLowerCase(Locale.ROOT);
        if (unaccentAvailable) {
            k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd');
        }
        k = k.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
