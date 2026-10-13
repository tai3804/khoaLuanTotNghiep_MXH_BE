package iuh.fit.adminservice.infrastructure.event;

import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Ghi vào nhật ký kiểm duyệt việc kiểm duyệt viên chấp nhận / bác bỏ kháng nghị của tác giả
 * (post-service phát "post.appeal.reviewed"), để quyết định này cũng có dấu vết như các quyết định xử lý báo cáo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostAppealReviewedEventListener {

    private final ModerationLogRepository moderationLogRepository;

    @KafkaListener(topics = "post.appeal.reviewed", groupId = "admin-appeal-reviewed-group", properties = "auto.offset.reset=earliest")
    public void handleAppealReviewed(Map<String, Object> event) {
        try {
            UUID postId = UUID.fromString(String.valueOf(event.get("postId")));
            UUID moderatorId = event.get("moderatorId") != null ? UUID.fromString(String.valueOf(event.get("moderatorId"))) : null;
            boolean approved = Boolean.parseBoolean(String.valueOf(event.get("approved")));
            String note = event.get("note") != null ? String.valueOf(event.get("note")).trim() : "";

            String decision = approved ? "Chấp nhận kháng nghị, khôi phục bài viết" : "Bác bỏ kháng nghị, giữ nguyên quyết định";
            moderationLogRepository.save(ModerationLog.builder()
                    .moderatorId(moderatorId)
                    .targetType(TargetType.POST)
                    .targetId(postId)
                    .action(approved ? ModerationAction.APPEAL_ACCEPTED : ModerationAction.APPEAL_REJECTED)
                    .reason("APPEAL")
                    .note(note.isEmpty() ? decision : decision + ": " + note)
                    .build());
            log.info("Recorded appeal decision for post {} (approved: {})", postId, approved);
        } catch (Exception e) {
            log.error("Failed to record appeal decision {}: {}", event, e.getMessage(), e);
        }
    }
}
