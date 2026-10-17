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
}
