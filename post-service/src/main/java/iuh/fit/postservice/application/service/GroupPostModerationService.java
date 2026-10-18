package iuh.fit.postservice.application.service;

import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.GroupReportStatus;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.GroupPostReportRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** Gỡ bài viết khỏi nhóm: dùng chung cho nút "Xóa bài viết" và cho việc xử lý báo cáo của thành viên. */
@Service
@RequiredArgsConstructor
public class GroupPostModerationService {

    static final String DEFAULT_REASON = "Bài viết không phù hợp với quy định của nhóm.";

    private final PostRepository postRepository;
    private final GroupPostReportRepository groupPostReportRepository;
    private final GroupNotificationPublisher groupNotificationPublisher;
    private final PostCacheEvictor postCacheEvictor;

    public void remove(Post post, UUID moderatorId, String reason) {
        post.setDeleted(true);
        post.setArchived(true);
        post.setStatus(PostStatus.REJECTED);
        post.setGroupPinnedAt(null);
        post.setModeratedBy(moderatorId);
        post.setModerationAction("DELETE_POST");
        post.setModerationReason(reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim());
        post.setAppealStatus(null);
        post.setAppealMessage(null);
        post.setAppealReviewNote(null);
        postRepository.save(post);
        // Bài đã bị gỡ thì mọi báo cáo về nó coi như đã được xử lý
        groupPostReportRepository.resolveOpenReports(post.getId(), GroupReportStatus.OPEN, GroupReportStatus.ACTIONED, moderatorId, LocalDateTime.now());
        postCacheEvictor.evictAll();
        groupNotificationPublisher.postRemoved(post, moderatorId);
    }
}
