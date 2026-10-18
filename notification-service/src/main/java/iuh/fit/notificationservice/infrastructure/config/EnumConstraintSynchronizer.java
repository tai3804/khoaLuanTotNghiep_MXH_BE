package iuh.fit.notificationservice.infrastructure.config;

import iuh.fit.notificationservice.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate (ddl-auto: update) tạo CHECK constraint cho cột enum khi tạo bảng nhưng không cập nhật khi enum có thêm giá trị:
 * DB cũ chỉ cho phép các loại thông báo có từ lúc tạo bảng nên mọi thông báo nhóm (GROUP_*) bị DB từ chối và không bao giờ
 * tới người dùng. Mỗi lần khởi động, tạo lại constraint theo đúng các giá trị enum hiện tại.
 * Chỉ dùng tên enum và tên bảng/cột cố định trong code nên không có rủi ro SQL injection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnumConstraintSynchronizer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        sync("notifications", "type", "notifications_type_check", NotificationType.values());
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
