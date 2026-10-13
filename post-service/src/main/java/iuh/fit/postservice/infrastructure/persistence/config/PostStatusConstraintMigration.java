package iuh.fit.postservice.infrastructure.persistence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate's ddl-auto=update adds enum values to Java mappings but does not
 * reliably update existing PostgreSQL CHECK constraints.  Groups can create a
 * post in PENDING_APPROVAL, so keep the existing database constraint aligned
 * with PostStatus at application startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostStatusConstraintMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE posts
                    ADD CONSTRAINT posts_status_check
                    CHECK (status IN ('PUBLISHED', 'PENDING_APPROVAL', 'REJECTED', 'PROCESSING', 'FAILED'))
                    """);
            log.info("Updated posts_status_check with group post approval statuses");
        } catch (Exception exception) {
            // Never prevent the service from starting if a fresh schema has not
            // yet been initialized or an administrator manages the constraint.
            log.warn("Could not update posts_status_check: {}", exception.getMessage());
        }
    }
}
