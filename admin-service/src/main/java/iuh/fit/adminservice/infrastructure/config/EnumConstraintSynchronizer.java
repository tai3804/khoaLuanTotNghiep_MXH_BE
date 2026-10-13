package iuh.fit.adminservice.infrastructure.config;

import iuh.fit.adminservice.domain.enums.ModerationAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate (ddl-auto: update) tạo CHECK constraint cho cột enum khi tạo bảng nhưng không cập nhật khi enum có thêm giá trị,
 * khiến INSERT giá trị mới bị DB từ chối. Mỗi lần khởi động, tạo lại constraint theo đúng các giá trị enum hiện tại.
 * Chỉ dùng tên enum và tên bảng/cột cố định trong code nên không có rủi ro SQL injection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnumConstraintSynchronizer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        sync("moderation_logs", "action", "moderation_logs_action_check", ModerationAction.values());
    }

    private void sync(String table, String column, String constraint, Enum<?>[] values) {
        String allowed = Arrays.stream(values).map(value -> "'" + value.name() + "'").collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " CHECK (" + column + " IN (" + allowed + "))");
            log.info("Synchronized check constraint {} on {}.{} with {} enum values", constraint, table, column, values.length);
        } catch (Exception e) {
            log.warn("Could not synchronize check constraint {} on {}.{}: {}", constraint, table, column, e.getMessage());
        }
    }
}
