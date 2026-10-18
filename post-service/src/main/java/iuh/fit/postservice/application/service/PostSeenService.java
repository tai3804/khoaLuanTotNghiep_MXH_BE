package iuh.fit.postservice.application.service;

import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Theo dõi những bài viết người dùng đã xem trên bảng tin để các lần tải sau không bắt họ xem lại.
 * Lượt xem chỉ có hiệu lực trong {@code feed.seen.retention-days} ngày (mặc định 30), sau đó bài có thể xuất hiện lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSeenService {

    /** Một request ghi tối đa số bài này (client gửi theo lô nhỏ). */
    public static final int MAX_BATCH = 100;
    /** Cứ khoảng 1/N lần ghi thì dọn các lượt xem đã quá hạn của người đó, khỏi cần job định kỳ. */
    private static final int PURGE_ONE_IN = 20;

    private final PostViewRepository postViewRepository;
    private final PostRepository postRepository;

    @Value("${feed.seen.retention-days:30}")
    private int retentionDays;

    /** Ghi các bài đã xem; id không tồn tại bị bỏ qua. Trả về số bài hợp lệ đã ghi nhận. */
    @Transactional
    public int markSeen(UUID userId, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) return 0;
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID id : postIds) {
            if (id != null) ids.add(id);
            if (ids.size() >= MAX_BATCH) break;
        }
        Set<UUID> existing = new HashSet<>(postRepository.findExistingIds(ids));

        LocalDateTime now = LocalDateTime.now();
        for (UUID id : ids) {
            if (existing.contains(id)) {
                postViewRepository.insertIgnore(UUID.randomUUID(), userId, id, now);
            }
        }
        if (ThreadLocalRandom.current().nextInt(PURGE_ONE_IN) == 0) {
            int purged = postViewRepository.deleteOlderThan(userId, now.minusDays(retentionDays));
            if (purged > 0) log.debug("Purged {} expired post views of user {}", purged, userId);
        }
        return existing.size();
    }

    /** Trong số `postIds`, những bài người dùng đã xem (còn hiệu lực). */
    @Transactional(readOnly = true)
    public Set<UUID> seenAmong(UUID userId, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) return Set.of();
        return new HashSet<>(postViewRepository.findSeenPostIds(userId, postIds, LocalDateTime.now().minusDays(retentionDays)));
    }

    /** Mốc thời gian sớm nhất mà một lượt xem còn được tính. */
    public LocalDateTime retentionCutoff() {
        return LocalDateTime.now().minusDays(retentionDays);
    }
}
