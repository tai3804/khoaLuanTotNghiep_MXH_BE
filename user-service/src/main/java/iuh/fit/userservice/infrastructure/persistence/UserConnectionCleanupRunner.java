package iuh.fit.userservice.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserConnectionCleanupRunner implements CommandLineRunner {

    JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 1. Purge duplicate directed connections (same requester_id, target_id, type)
            // Keep the record with status = 'ACCEPTED' or the latest created
            String purgeDirectedDuplicatesSql = 
                    "DELETE FROM user_connections " +
                    "WHERE id IN (" +
                    "    SELECT id FROM (" +
                    "        SELECT id, ROW_NUMBER() OVER (" +
                    "            PARTITION BY requester_id, target_id, type " +
                    "            ORDER BY CASE WHEN status = 'ACCEPTED' THEN 0 ELSE 1 END, created_at DESC" +
                    "        ) as rnum " +
                    "        FROM user_connections" +
                    "    ) t WHERE t.rnum > 1" +
                    ")";

            int deletedDirected = jdbcTemplate.update(purgeDirectedDuplicatesSql);
            if (deletedDirected > 0) {
                log.info("Deduplication cleanup: Removed {} duplicate directed user_connection rows.", deletedDirected);
            }

            // 2. Purge duplicate bidirectional friendships (A-B and B-A where type = 'FRIEND')
            String purgeFriendshipDuplicatesSql = 
                    "DELETE FROM user_connections " +
                    "WHERE id IN (" +
                    "    SELECT id FROM (" +
                    "        SELECT id, ROW_NUMBER() OVER (" +
                    "            PARTITION BY " +
                    "                CASE WHEN requester_id < target_id THEN requester_id ELSE target_id END, " +
                    "                CASE WHEN requester_id < target_id THEN target_id ELSE requester_id END, " +
                    "                type " +
                    "            ORDER BY CASE WHEN status = 'ACCEPTED' THEN 0 ELSE 1 END, created_at DESC" +
                    "        ) as rnum " +
                    "        FROM user_connections " +
                    "        WHERE type = 'FRIEND'" +
                    "    ) t WHERE t.rnum > 1" +
                    ")";

            int deletedFriendships = jdbcTemplate.update(purgeFriendshipDuplicatesSql);
            if (deletedFriendships > 0) {
                log.info("Deduplication cleanup: Removed {} duplicate bidirectional friendship rows.", deletedFriendships);
            }
        } catch (Exception e) {
            log.warn("Notice: Automated user_connections cleanup encountered: {}", e.getMessage());
        }
    }
}
