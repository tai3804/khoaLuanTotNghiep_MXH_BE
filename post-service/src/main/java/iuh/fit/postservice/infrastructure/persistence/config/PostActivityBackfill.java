package iuh.fit.postservice.infrastructure.persistence.config;

import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Cột last_activity_at mới thêm: bài cũ chưa có giá trị thì lấy thời điểm đăng, để sắp xếp theo hoạt động gần đây không lệch. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostActivityBackfill implements ApplicationRunner {

    private final PostRepository postRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = postRepository.backfillLastActivity();
            if (updated > 0) log.info("Backfilled last_activity_at for {} post(s)", updated);
        } catch (Exception e) {
            log.warn("Could not backfill last_activity_at: {}", e.getMessage());
        }
    }
}
