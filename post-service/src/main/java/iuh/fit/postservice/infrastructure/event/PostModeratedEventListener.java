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

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

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
            if ("HIDE_POST".equals(event.getAction())) {
                post.setArchived(true);
            } else if ("RESTORE_POST".equals(event.getAction()) || "UNHIDE_POST".equals(event.getAction())) {
                post.setArchived(false);
                post.setDeleted(false);
            } else {
                post.setDeleted(true);
            }
            if ("HIDE_POST".equals(event.getAction()) || "DELETE_POST".equals(event.getAction())) {
                post.setModerationAction(event.getAction());
                post.setModerationReason(event.getReason());
                post.setModeratedBy(event.getModeratorId());
                notifyAuthor(post, event);
            }
            postRepository.save(post);
            log.info("Successfully applied {} to moderated post: {}", event.getAction(), event.getPostId());
        } else {
            log.warn("Post with ID {} not found for moderation event", event.getPostId());
        }
    }

    private void notifyAuthor(Post post, PostModeratedEvent event) {
        if (post.getAuthorId() == null) return;
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientId", post.getAuthorId().toString());
        notification.put("actorId", event.getModeratorId() == null ? null : event.getModeratorId().toString());
        notification.put("type", "SYSTEM");
        notification.put("title", "Bài viết của bạn đã bị " + ("HIDE_POST".equals(event.getAction()) ? "ẩn" : "gỡ"));
        notification.put("content", "Lý do: " + (event.getReason() == null ? "Vi phạm tiêu chuẩn cộng đồng" : event.getReason()) + ". Bạn có thể gửi kháng nghị để được xem xét lại.");
        notification.put("targetId", post.getId().toString());
        notification.put("targetUrl", "/support-inbox?postId=" + post.getId());
        notification.put("avatarUrl", null);
        kafkaTemplate.send("notification.in-app.send", notification);
    }
}
