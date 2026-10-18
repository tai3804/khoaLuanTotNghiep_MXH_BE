package iuh.fit.postservice.infrastructure.event;

import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Nhóm cộng đồng bị xóa (user-service phát "group.deleted"): gỡ toàn bộ bài viết của nhóm.
 * Nếu không, bài vẫn hiện trong bảng tin của thành viên và tác giả vì quan hệ thành viên vẫn còn trong DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupDeletedEventListener {

    private final PostRepository postRepository;
    private final PostCacheEvictor postCacheEvictor;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "group.deleted", groupId = "post-group-deleted-group", properties = "auto.offset.reset=earliest")
    public void handleGroupDeleted(Map<String, Object> event) {
        try {
            UUID groupId = UUID.fromString(String.valueOf(event.get("groupId")));
            Integer removed = transactionTemplate.execute(status -> postRepository.softDeleteByGroupId(groupId));
            log.info("Group {} deleted: removed {} post(s) of that group", groupId, removed);
            postCacheEvictor.evictAllWithRecheck();
        } catch (Exception e) {
            log.error("Failed to process group.deleted event {}: {}", event, e.getMessage(), e);
        }
    }
}
