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

    private volatile boolean vectorAvailable = false;

    public boolean isVectorAvailable() {
        if (!vectorAvailable) {
            initVector();
        }
        return vectorAvailable;
    }

    private synchronized void initVector() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            Integer found = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
            vectorAvailable = found != null && found > 0;

            if (vectorAvailable) {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS group_embeddings (
                        group_id UUID PRIMARY KEY,
                        embedding vector(768) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_group_embeddings_hnsw
                    ON group_embeddings USING hnsw (embedding vector_cosine_ops)
                """);

                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS user_embeddings (
                        user_id UUID PRIMARY KEY,
                        embedding vector(768) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_user_embeddings_hnsw
                    ON user_embeddings USING hnsw (embedding vector_cosine_ops)
                """);

                log.info("pgvector extension and group_embeddings table with HNSW index successfully initialized for groups!");
            } else {
                log.warn("pgvector extension is not available in PostgreSQL user_db.");
            }
        } catch (Exception e) {
            vectorAvailable = false;
            log.warn("Could not enable pgvector extension for groups: {}", e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        initVector();
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
