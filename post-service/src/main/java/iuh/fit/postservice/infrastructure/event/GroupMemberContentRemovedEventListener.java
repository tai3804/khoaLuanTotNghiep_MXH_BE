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
 * Quản trị viên xóa/cấm thành viên kèm "xóa nội dung" (user-service phát "group.member-content-removed"):
 * gỡ mọi bài viết của người đó trong nhóm đó, giống tùy chọn "Xóa nội dung" của Facebook.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMemberContentRemovedEventListener {

    private final PostRepository postRepository;
    private final PostCacheEvictor postCacheEvictor;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "group.member-content-removed", groupId = "post-group-member-content-removed-group", properties = "auto.offset.reset=earliest")
    public void handle(Map<String, Object> event) {
        try {
            UUID groupId = UUID.fromString(String.valueOf(event.get("groupId")));
            UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
            Integer removed = transactionTemplate.execute(status -> postRepository.softDeleteByGroupIdAndAuthorId(groupId, userId));
            log.info("Removed {} post(s) of user {} in group {}", removed, userId, groupId);
            postCacheEvictor.evictAllWithRecheck();
        } catch (Exception e) {
            log.error("Failed to process group.member-content-removed event {}: {}", event, e.getMessage(), e);
        }
    }
}
