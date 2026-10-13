package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.ModerationAppealStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostModeratedEventListener {

    /** moderatorId của các quyết định tự động của hệ thống (vd: tự ẩn khi vượt ngưỡng báo cáo). */
    static final UUID SYSTEM_MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    PostRepository postRepository;
    PostCacheEvictor postCacheEvictor;
    ModerationNotificationPublisher notificationPublisher;
    ReputationEventPublisher reputationEventPublisher;

    @KafkaListener(topics = "post.moderated", groupId = "post-moderation-group")
    public void handlePostModerated(PostModeratedEvent event) {
        log.info("Received PostModeratedEvent for postId: {}, action: {}, reason: {}",
                event.getPostId(), event.getAction(), event.getReason());

        Optional<Post> postOpt = postRepository.findById(event.getPostId());
        if (postOpt.isEmpty()) {
            log.warn("Post with ID {} not found for moderation event", event.getPostId());
            return;
        }

        Post post = postOpt.get();
        String action = event.getAction();
        boolean wasArchived = post.isArchived();
        boolean wasDeleted = post.isDeleted();
        boolean restoring = "RESTORE_POST".equals(action) || "UNHIDE_POST".equals(action);

        if (restoring) {
            // Chỉ khôi phục bài đang bị kiểm duyệt: bài do tác giả tự xoá/lưu trữ không được hồi sinh
            if (post.getModerationAction() == null) {
                log.info("Skip {} for post {}: the post is not under a moderation decision", action, post.getId());
                return;
            }
            post.setArchived(false);
            post.setDeleted(false);
            post.setModerationAction(null);
            post.setModerationReason(null);
            post.setModeratedBy(null);
            if (post.getAppealStatus() == ModerationAppealStatus.PENDING) {
                post.setAppealStatus(ModerationAppealStatus.ACCEPTED);
                post.setAppealReviewNote("Bài viết đã được kiểm duyệt viên khôi phục.");
            }
        } else if ("HIDE_POST".equals(action)) {
            post.setArchived(true);
            applyModeration(post, event);
        } else if ("DELETE_POST".equals(action)) {
            post.setDeleted(true);
            applyModeration(post, event);
        } else {
            log.warn("Ignoring unsupported moderation action {} for post {}", action, post.getId());
            return;
        }
        postRepository.save(post);
        postCacheEvictor.evictAllWithRecheck();
        log.info("Successfully applied {} to moderated post: {}", action, event.getPostId());

        if (post.getAuthorId() == null) {
            return;
        }

        // Điểm uy tín: chỉ quyết định của kiểm duyệt viên / hệ thống mới bị trừ; AI chỉ tạm ẩn chờ duyệt nên chưa trừ.
        // Khôi phục (hoàn tác, bỏ qua báo cáo, kháng nghị) thì hoàn lại điểm đã trừ cho bài này.
        boolean aiOrigin = event.getReason() != null && event.getReason().contains("AI_AUTO_MODERATION");
        if (restoring) {
            reputationEventPublisher.revert(post.getAuthorId(), post.getId(), event.getReason());
        } else if (!aiOrigin) {
            String penaltyAction = "DELETE_POST".equals(action) ? "DELETE_POST"
                    : SYSTEM_MODERATOR_ID.equals(event.getModeratorId()) ? "AUTO_HIDE" : "HIDE_POST";
            reputationEventPublisher.penalize(post.getAuthorId(), penaltyAction, post.getId(), event.getReason(), event.getModeratorId());
        }

        // Gửi thông báo đến tác giả bài viết
        // Nếu sự kiện được khởi tạo từ AI_AUTO_MODERATION, ai-service đã trực tiếp gửi thông báo
        // kèm chi tiết lý do, nhóm vi phạm và hướng dẫn sửa đổi cho tác giả, tránh gửi trùng lặp thông báo.
        if (aiOrigin) {
            log.info("Skipping duplicate notification for AI_AUTO_MODERATION on post: {}", post.getId());
            return;
        }

        String appealUrl = "/support-inbox?postId=" + post.getId();
        String actorId = event.getModeratorId() == null ? null : event.getModeratorId().toString();

        if ("HIDE_POST".equals(action) && !wasArchived) {
            notificationPublisher.send(
                    post.getAuthorId(),
                    actorId,
                    "Bài viết của bạn đã bị tạm ẩn",
                    "Lý do: " + (event.getReason() != null ? event.getReason() : "Do nhận nhiều báo cáo vi phạm từ cộng đồng.") + ". Bạn có thể gửi kháng nghị để được xem xét lại.",
                    post.getId().toString(),
                    appealUrl
            );
        } else if ("DELETE_POST".equals(action) && !wasDeleted) {
            notificationPublisher.send(
                    post.getAuthorId(),
                    actorId,
                    "Bài viết của bạn đã bị gỡ bỏ",
                    "Lý do: " + (event.getReason() != null ? event.getReason() : "Vi phạm tiêu chuẩn cộng đồng") + ". Bạn có thể gửi kháng nghị để được xem xét lại.",
                    post.getId().toString(),
                    appealUrl
            );
        } else if (restoring && (wasArchived || wasDeleted)) {
            notificationPublisher.send(
                    post.getAuthorId(),
                    null,
                    "Bài viết của bạn đã được hiển thị lại",
                    "Bài viết của bạn đã được quản trị viên khôi phục hiển thị.",
                    post.getId().toString(),
                    "/posts/" + post.getId()
            );
        }
    }

    private void applyModeration(Post post, PostModeratedEvent event) {
        post.setModerationAction(event.getAction());
        post.setModerationReason(event.getReason());
        post.setModeratedBy(event.getModeratorId());
    }
}
