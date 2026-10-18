package iuh.fit.userservice.infrastructure.persistence;

import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Chuẩn bị cơ sở dữ liệu cho phần nhóm cộng đồng mỗi lần khởi động:
 * <ul>
 *   <li>Bật extension unaccent: tìm nhóm/thành viên không phân biệt dấu ("ca phe" tìm được "Cà phê").</li>
 *   <li>Tạo lại CHECK constraint của cột trạng thái thành viên: Hibernate (ddl-auto: update) không cập nhật constraint cũ
 *       khi enum có thêm giá trị (INVITED), nên thêm lời mời sẽ bị DB từ chối.</li>
 * </ul>
 * Chỉ dùng tên bảng/cột và giá trị enum cố định trong code nên không có rủi ro SQL injection.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GroupSchemaSynchronizer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            log.info("Extension unaccent is available for group search");
        } catch (Exception e) {
            log.error("Could not enable the unaccent extension, searching groups and members will fail until it is created "
                    + "(run CREATE EXTENSION unaccent as a superuser): {}", e.getMessage());
        }

        String allowed = Arrays.stream(GroupMemberStatus.values()).map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE group_members DROP CONSTRAINT IF EXISTS group_members_status_check");
            jdbcTemplate.execute("ALTER TABLE group_members ADD CONSTRAINT group_members_status_check CHECK (status IN (" + allowed + "))");
            log.info("Synchronized group_members_status_check with {} statuses", GroupMemberStatus.values().length);
        } catch (Exception e) {
            log.warn("Could not synchronize group_members_status_check: {}", e.getMessage());
        }
    }
}
