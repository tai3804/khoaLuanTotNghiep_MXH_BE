package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Báo cho bạn bè khi ai đó bắt đầu phát trực tiếp (giống Facebook "… đang phát trực tiếp").
 * Phát trực tiếp là một bài viết có nội dung bắt đầu bằng {@link ReputationGuard#LIVE_MARKER}, nên thông báo được gửi ngay khi
 * bài đó được tạo. Mọi lỗi chỉ ghi log: thông báo không bao giờ làm hỏng việc bắt đầu phát.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveNotificationPublisher {

    /** Số bạn bè tối đa được báo mỗi lần phát. */
    static final int MAX_RECIPIENTS = 1000;
    private static final int MAX_TITLE_LENGTH = 100;

    private final UserConnectionClient userConnectionClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Gọi trong request của người phát (cần token của họ để lấy danh sách bạn bè). Chỉ báo khi bài hiển thị được cho bạn bè:
     * bài riêng tư, bài trong nhóm (nhóm có thông báo riêng) hay bài giới hạn người xem thì bỏ qua.
     */
    public void liveStarted(Post post) {
        if (post == null || post.getContent() == null || !post.getContent().startsWith(ReputationGuard.LIVE_MARKER)) return;
        if (post.getGroupId() != null) return;
        if (post.getPrivacy() != PostPrivacy.PUBLIC && post.getPrivacy() != PostPrivacy.FRIENDS) return;

        List<UUID> friendIds = friendIdsOfCurrentUser();
        if (friendIds.isEmpty()) return;

        String title = liveTitle(post.getContent());
        String content = title.isEmpty()
                ? "Một người dùng đang phát trực tiếp."
                : "Một người dùng đang phát trực tiếp: \"" + title + "\"";

        int sent = 0;
        for (UUID friendId : friendIds) {
            if (friendId == null || friendId.equals(post.getAuthorId())) continue;
            if (sent >= MAX_RECIPIENTS) break;
            send(friendId, post, content);
            sent++;
        }
        log.info("Notified {} friends that user {} started live post {}", sent, post.getAuthorId(), post.getId());
    }

    private List<UUID> friendIdsOfCurrentUser() {
        try {
            ApiResponse<Map<String, List<UUID>>> response = userConnectionClient.getSocialGraph();
            Map<String, List<UUID>> graph = response == null ? null : response.getData();
            List<UUID> friendIds = graph == null ? null : graph.get("friendIds");
            return friendIds == null ? List.of() : friendIds;
        } catch (Exception e) {
            log.warn("Could not load friends to notify about a live stream: {}", e.getMessage());
            return List.of();
        }
    }

    /** Tiêu đề buổi phát: dòng đầu sau dấu hiệu LIVE (phần sau đó là mô tả), bỏ nháy kép để FE tách được phần trích dẫn. */
    static String liveTitle(String postContent) {
        String afterMarker = postContent.substring(ReputationGuard.LIVE_MARKER.length()).strip();
        int newline = afterMarker.indexOf('\n');
        String title = (newline >= 0 ? afterMarker.substring(0, newline) : afterMarker).strip().replace('"', '\'');
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH).strip() + "…" : title;
    }

    private void send(UUID recipientId, Post post, String content) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("recipientId", recipientId.toString());
            event.put("actorId", post.getAuthorId() == null ? null : post.getAuthorId().toString());
            event.put("type", "LIVE_STARTED");
            event.put("title", "Bạn bè đang phát trực tiếp");
            event.put("content", content);
            event.put("targetId", post.getId() == null ? null : post.getId().toString());
            event.put("targetUrl", "/posts/" + post.getId());
            event.put("avatarUrl", null);
            kafkaTemplate.send("notification.in-app.send", event);
        } catch (Exception e) {
            log.warn("Could not publish LIVE_STARTED notification for {}: {}", recipientId, e.getMessage());
        }
    }
}
