package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.client.user.GroupInternalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thông báo của nhóm về bài viết (giống Facebook): quản trị viên được báo khi có bài chờ duyệt, tác giả được báo khi bài
 * được duyệt/bị từ chối/bị gỡ, và thành viên chọn "mọi bài viết" được báo khi có bài mới.
 * Mọi lỗi chỉ ghi log: thông báo không bao giờ làm hỏng thao tác đăng/duyệt bài.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupNotificationPublisher {

    private final GroupInternalClient groupInternalClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Bài mới cần duyệt: báo cho quản trị/kiểm duyệt viên của nhóm. */
    public void postPending(Post post) {
        GroupInternalClient.NotifyContext ctx = context(post.getGroupId(), post.getAuthorId());
        if (ctx == null) return;
        String who = actorName(ctx);
        for (UUID staffId : safe(ctx.staffIds())) {
            send(staffId, post.getAuthorId(), "GROUP_POST_PENDING", "Bài viết đang chờ duyệt",
                    who + " đã gửi một bài viết chờ duyệt trong nhóm \"" + ctx.name() + "\".",
                    post.getId(), "/groups/" + post.getGroupId() + "?tab=pending-posts", ctx.actorAvatar());
        }
    }

    /** Có thành viên báo cáo một bài viết: báo cho quản trị/kiểm duyệt viên của nhóm. */
    public void postReported(Post post, UUID reporterId, String reason) {
        GroupInternalClient.NotifyContext ctx = context(post.getGroupId(), reporterId);
        if (ctx == null) return;
        String who = actorName(ctx);
        for (UUID staffId : safe(ctx.staffIds())) {
            send(staffId, reporterId, "GROUP_POST_REPORTED", "Bài viết bị báo cáo",
                    who + " đã báo cáo một bài viết trong nhóm \"" + ctx.name() + "\" (" + reason + ").",
                    post.getId(), "/groups/" + post.getGroupId() + "?tab=reports", ctx.actorAvatar());
        }
    }

    /** Bài mới được đăng (hoặc vừa được duyệt): báo cho thành viên đã chọn nhận mọi bài viết của nhóm. */
    public void postPublished(Post post) {
        GroupInternalClient.NotifyContext ctx = context(post.getGroupId(), post.getAuthorId());
        if (ctx == null) return;
        String who = actorName(ctx);
        for (UUID memberId : safe(ctx.subscriberIds())) {
            send(memberId, post.getAuthorId(), "GROUP_POST", "Bài viết mới trong " + ctx.name(),
                    who + " đã đăng một bài viết mới trong nhóm \"" + ctx.name() + "\".",
                    post.getId(), "/posts/" + post.getId(), ctx.actorAvatar());
        }
    }

    /** Bài chờ duyệt đã được quyết định: báo cho tác giả (và nếu được duyệt, báo thành viên đăng ký nhận bài mới). */
    public void postReviewed(Post post, UUID reviewerId, boolean approved) {
        GroupInternalClient.NotifyContext ctx = context(post.getGroupId(), reviewerId);
        if (ctx != null) {
            send(post.getAuthorId(), reviewerId,
                    approved ? "GROUP_POST_APPROVED" : "GROUP_POST_REJECTED",
                    approved ? "Bài viết đã được duyệt" : "Bài viết không được duyệt",
                    approved ? "Bài viết của bạn trong nhóm \"" + ctx.name() + "\" đã được duyệt và hiển thị với mọi người."
                            : "Bài viết của bạn trong nhóm \"" + ctx.name() + "\" không được quản trị viên duyệt.",
                    post.getId(), "/groups/" + post.getGroupId(), ctx.actorAvatar());
        }
        if (approved) postPublished(post);
    }

    /** Bài bị quản trị viên gỡ khỏi nhóm: báo cho tác giả kèm lý do và đường dẫn tới mục kháng nghị. */
    public void postRemoved(Post post, UUID moderatorId) {
        GroupInternalClient.NotifyContext ctx = context(post.getGroupId(), moderatorId);
        send(post.getAuthorId(), moderatorId, "GROUP_POST_REMOVED",
                "Bài viết trong nhóm" + (ctx == null ? "" : " \"" + ctx.name() + "\"") + " đã bị gỡ",
                "Lý do: " + post.getModerationReason(),
                post.getId(), "/groups/" + post.getGroupId() + "?tab=appeals", ctx == null ? null : ctx.actorAvatar());
    }

    private GroupInternalClient.NotifyContext context(UUID groupId, UUID actorId) {
        try {
            ApiResponse<GroupInternalClient.NotifyContext> response = groupInternalClient.getNotifyContext(groupId, actorId);
            return response == null ? null : response.getData();
        } catch (Exception e) {
            log.warn("Could not load notify context of group {}: {}", groupId, e.getMessage());
            return null;
        }
    }

    private static String actorName(GroupInternalClient.NotifyContext ctx) {
        return ctx.actorName() == null || ctx.actorName().isBlank() ? "Một thành viên" : ctx.actorName();
    }

    private static List<UUID> safe(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    private void send(UUID recipientId, UUID actorId, String type, String title, String content, UUID targetId, String targetUrl, String avatarUrl) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("recipientId", recipientId.toString());
            event.put("actorId", actorId == null ? null : actorId.toString());
            event.put("type", type);
            event.put("title", title);
            event.put("content", content);
            event.put("targetId", targetId == null ? null : targetId.toString());
            event.put("targetUrl", targetUrl);
            event.put("avatarUrl", avatarUrl);
            kafkaTemplate.send("notification.in-app.send", event);
        } catch (Exception e) {
            log.warn("Could not publish group notification {} for {}: {}", type, recipientId, e.getMessage());
        }
    }
}
