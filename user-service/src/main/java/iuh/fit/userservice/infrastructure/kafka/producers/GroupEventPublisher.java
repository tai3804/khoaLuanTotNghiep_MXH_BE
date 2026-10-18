package iuh.fit.userservice.infrastructure.kafka.producers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sự kiện vòng đời của nhóm cộng đồng cho các service khác (post-service ẩn bài của nhóm đã xóa). */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishGroupDeleted(UUID groupId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("groupId", groupId.toString());
            kafkaTemplate.send("group.deleted", event);
            log.info("Published group.deleted for group {}", groupId);
        } catch (Exception e) {
            log.error("Failed to publish group.deleted for group {}: {}", groupId, e.getMessage());
        }
    }

    /** Quản trị viên xóa/cấm thành viên kèm "xóa nội dung": post-service gỡ bài viết của người đó trong nhóm. */
    public void publishMemberContentRemoved(UUID groupId, UUID userId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("groupId", groupId.toString());
            event.put("userId", userId.toString());
            kafkaTemplate.send("group.member-content-removed", event);
            log.info("Published group.member-content-removed for user {} in group {}", userId, groupId);
        } catch (Exception e) {
            log.error("Failed to publish group.member-content-removed for user {} in group {}: {}", userId, groupId, e.getMessage());
        }
    }
}
