package iuh.fit.postservice.infrastructure.event;

import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kiểm duyệt viên cảnh cáo tác giả của một bài viết (admin-service phát "post.warned").
 * Bài viết giữ nguyên trạng thái; tác giả nhận thông báo cảnh cáo và bị trừ điểm uy tín.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostWarnedEventListener {

    PostRepository postRepository;
    ModerationNotificationPublisher notificationPublisher;
    ReputationEventPublisher reputationEventPublisher;

    @KafkaListener(topics = "post.warned", groupId = "post-warning-group")
    public void handlePostWarned(Map<String, Object> event) {
        log.info("Received post.warned event: {}", event);
        try {
            UUID postId = UUID.fromString(String.valueOf(event.get("postId")));
            String reason = event.get("reason") != null ? String.valueOf(event.get("reason")) : null;
            UUID moderatorId = event.get("moderatorId") != null ? UUID.fromString(String.valueOf(event.get("moderatorId"))) : null;

            postRepository.findById(postId).ifPresentOrElse(post -> {
                if (post.getAuthorId() == null) {
                    return;
                }
                notificationPublisher.send(
                        post.getAuthorId(),
                        moderatorId != null ? moderatorId.toString() : null,
                        "Bạn nhận được cảnh cáo từ kiểm duyệt viên",
                        "Bài viết của bạn bị kiểm duyệt viên cảnh cáo vì chưa phù hợp với tiêu chuẩn cộng đồng"
                                + (reason != null ? ". Lý do: " + reason : "")
                                + ". Vui lòng chỉnh sửa hoặc tuân thủ quy định để tránh bị ẩn hoặc gỡ bài.",
                        post.getId().toString(),
                        "/posts/" + post.getId()
                );
                reputationEventPublisher.penalize(post.getAuthorId(), "WARN_USER", post.getId(), reason, moderatorId);
            }, () -> log.warn("Post {} not found for warning event", postId));
        } catch (Exception e) {
            log.error("Failed to process post.warned event {}: {}", event, e.getMessage(), e);
        }
    }
}
