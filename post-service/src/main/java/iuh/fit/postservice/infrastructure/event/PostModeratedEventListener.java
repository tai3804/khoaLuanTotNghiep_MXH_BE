package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostModeratedEventListener {

    PostRepository postRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "post.moderated", groupId = "post-moderation-group")
    public void handlePostModerated(PostModeratedEvent event) {
        log.info("Received PostModeratedEvent for postId: {}, action: {}, reason: {}",
                event.getPostId(), event.getAction(), event.getReason());

        Optional<Post> postOpt = postRepository.findById(event.getPostId());
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            boolean wasArchived = post.isArchived();
            boolean wasDeleted = post.isDeleted();

            if ("HIDE_POST".equals(event.getAction())) {
                post.setArchived(true);
            } else if ("RESTORE_POST".equals(event.getAction()) || "UNHIDE_POST".equals(event.getAction())) {
                post.setArchived(false);
                post.setDeleted(false);
            } else {
                post.setDeleted(true);
            }
            postRepository.save(post);
            log.info("Successfully applied {} to moderated post: {}", event.getAction(), event.getPostId());

            // Gửi thông báo đến tác giả bài viết
            if (post.getAuthorId() != null) {
                // Nếu sự kiện được khởi tạo từ AI_AUTO_MODERATION, ai-service đã trực tiếp gửi thông báo
                // kèm chi tiết lý do, nhóm vi phạm và hướng dẫn sửa đổi cho tác giả, tránh gửi trùng lặp thông báo.
                if (event.getReason() != null && event.getReason().contains("AI_AUTO_MODERATION")) {
                    log.info("Skipping duplicate notification for AI_AUTO_MODERATION on post: {}", post.getId());
                    return;
                }

                if ("HIDE_POST".equals(event.getAction()) && !wasArchived) {
                    sendModerationNotification(
                            post.getAuthorId(),
                            "Bài viết của bạn đã bị tạm ẩn",
                            "Bài viết của bạn đã bị tạm ẩn: " + (event.getReason() != null ? event.getReason() : "Do nhận nhiều báo cáo vi phạm từ cộng đồng."),
                            post.getId().toString()
                    );
                } else if ("DELETE_POST".equals(event.getAction()) && !wasDeleted) {
                    sendModerationNotification(
                            post.getAuthorId(),
                            "Bài viết của bạn đã bị gỡ bỏ",
                            "Bài viết của bạn đã bị gỡ bỏ do vi phạm tiêu chuẩn cộng đồng: " + (event.getReason() != null ? event.getReason() : ""),
                            post.getId().toString()
                    );
                } else if (("RESTORE_POST".equals(event.getAction()) || "UNHIDE_POST".equals(event.getAction())) && (wasArchived || wasDeleted)) {
                    sendModerationNotification(
                            post.getAuthorId(),
                            "Bài viết của bạn đã được hiển thị lại",
                            "Bài viết của bạn đã được quản trị viên khôi phục hiển thị.",
                            post.getId().toString()
                    );
                }
            }
        } else {
            log.warn("Post with ID {} not found for moderation event", event.getPostId());
        }
    }

    private void sendModerationNotification(UUID recipientId, String title, String content, String targetId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId", recipientId.toString());
            payload.put("actorId", null);
            payload.put("type", "SYSTEM");
            payload.put("title", title);
            payload.put("content", content);
            payload.put("targetId", targetId != null ? targetId : "");
            payload.put("targetUrl", targetId != null ? "/posts/" + targetId : "");
            payload.put("avatarUrl", "");

            kafkaTemplate.send("notification.in-app.send", payload);
            log.info("Dispatched moderation notification to author {} via 'notification.in-app.send'", recipientId);
        } catch (Exception e) {
            log.error("Failed to dispatch moderation notification to author {}: {}", recipientId, e.getMessage());
        }
    }
}
