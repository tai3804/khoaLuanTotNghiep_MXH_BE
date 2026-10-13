package iuh.fit.adminservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import iuh.fit.commonframework.event.CommentModeratedEvent;
import iuh.fit.commonframework.event.ReportCreatedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModerationEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPostModerated(UUID postId, String action, String reason, UUID moderatorId) {
        PostModeratedEvent event = PostModeratedEvent.builder()
                .postId(postId)
                .action(action)
                .reason(reason)
                .moderatorId(moderatorId)
                .build();

        kafkaTemplate.send("post.moderated", event);
        log.info("Published PostModeratedEvent for postId: {}, action: {}", postId, action);
    }

    /**
     * Cảnh cáo tác giả bài viết. Dùng topic riêng thay vì post.moderated vì feed-service coi mọi sự kiện post.moderated là "bài bị gỡ".
     * post-service tra tác giả của bài rồi gửi thông báo và trừ điểm uy tín.
     */
    public void publishPostWarned(UUID postId, String reason, UUID moderatorId) {
        Map<String, Object> event = new HashMap<>();
        event.put("postId", postId.toString());
        event.put("reason", reason);
        event.put("moderatorId", moderatorId != null ? moderatorId.toString() : null);
        kafkaTemplate.send("post.warned", event);
        log.info("Published post.warned for postId: {}", postId);
    }

    public void publishReportCreated(ReportCreatedEvent event) {
        kafkaTemplate.send("report.created", event);
        log.info("Published ReportCreatedEvent for reportId: {}", event.getReportId());
    }

    public void publishCommentModerated(UUID commentId, String action, String reason, UUID moderatorId) {
        CommentModeratedEvent event = CommentModeratedEvent.builder()
                .commentId(commentId)
                .action(action)
                .reason(reason)
                .moderatorId(moderatorId)
                .build();
        kafkaTemplate.send("comment.moderated", event);
        log.info("Published CommentModeratedEvent for commentId: {}, action: {}", commentId, action);
    }
}
