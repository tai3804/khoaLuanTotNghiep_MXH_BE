package iuh.fit.postservice.infrastructure.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Gửi các quyết định kiểm duyệt do post-service xử lý sang admin-service để ghi vào nhật ký kiểm duyệt. */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModerationAuditPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    /** Kiểm duyệt viên chấp nhận hoặc bác bỏ kháng nghị của tác giả. */
    public void publishAppealReviewed(UUID postId, UUID moderatorId, boolean approved, String note) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("postId", postId.toString());
            event.put("moderatorId", moderatorId != null ? moderatorId.toString() : null);
            event.put("approved", approved);
            event.put("note", note);
            kafkaTemplate.send("post.appeal.reviewed", event);
            log.info("Published post.appeal.reviewed for post {} (approved: {})", postId, approved);
        } catch (Exception e) {
            log.error("Failed to publish post.appeal.reviewed for post {}: {}", postId, e.getMessage());
        }
    }
}
