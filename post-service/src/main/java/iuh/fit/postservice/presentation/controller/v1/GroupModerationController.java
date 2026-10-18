package iuh.fit.postservice.presentation.controller.v1;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailQuery;
import iuh.fit.postservice.application.service.GroupAccessService;
import iuh.fit.postservice.application.service.GroupNotificationPublisher;
import iuh.fit.postservice.application.service.GroupPostModerationService;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailQueryHandler;
import iuh.fit.postservice.domain.entities.GroupPostReport;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.GroupReportStatus;
import iuh.fit.postservice.domain.enums.ModerationAppealStatus;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.GroupPostReportRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import iuh.fit.postservice.presentation.mapper.PostPresentationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Báo cáo bài viết cho quản trị viên nhóm (giống "Báo cáo cho quản trị viên nhóm" của Facebook) và các số liệu cần xử lý
 * hiển thị ở thanh tab của quản trị viên.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Group Moderation", description = "Member reports on group posts and the moderation work queue of a group")
public class GroupModerationController {

    static final int MAX_DETAIL_LENGTH = 1000;
    static final int MAX_REASON_LENGTH = 100;

    PostRepository postRepository;
    GroupPostReportRepository groupPostReportRepository;
    GroupAccessService groupAccessService;
    GroupPostModerationService groupPostModerationService;
    GroupNotificationPublisher groupNotificationPublisher;
    GetPostDetailQueryHandler getPostDetailQueryHandler;
    PostPresentationMapper postPresentationMapper;
    PostCacheEvictor postCacheEvictor;
    JwtUtil jwtUtil;

    public record ReportRequest(String reason, String detail) { }

    public record ReasonCount(String reason, long count) { }

    public record ReportEntry(UUID reporterId, String reason, String detail, LocalDateTime createdAt) { }

    public record ReportedPostView(PostResponse post, int reportCount, List<ReasonCount> reasons, List<ReportEntry> reports) { }

    public record ModerationCounts(long pendingPosts, long pendingAppeals, long reportedPosts) { }

    @PostMapping("/{postId}/group-report")
    @Operation(summary = "Report a group post to the group's administrators", description = "Any member except the author; reporting the same post twice just updates the earlier report")
    public ResponseEntity<ApiResponse<Void>> reportGroupPost(@PathVariable UUID postId, @RequestBody ReportRequest request) {
        UUID reporterId = currentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null || post.getStatus() != PostStatus.PUBLISHED || reporterId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().trim();
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) detail = detail.substring(0, MAX_DETAIL_LENGTH);
        groupAccessService.requireMember(post.getGroupId());

        boolean firstOpenReport = !groupPostReportRepository.existsByPostIdAndStatus(postId, GroupReportStatus.OPEN);
        GroupPostReport report = groupPostReportRepository.findByPostIdAndReporterId(postId, reporterId)
                .orElseGet(() -> GroupPostReport.builder().postId(postId).groupId(post.getGroupId()).reporterId(reporterId).build());
        report.setReason(reason);
        report.setDetail(detail);
        report.setStatus(GroupReportStatus.OPEN);
        report.setHandledBy(null);
        report.setHandledAt(null);
        groupPostReportRepository.save(report);
        // Chỉ báo cho quản trị viên ở báo cáo đầu tiên của bài, để nhiều báo cáo không gây bão thông báo
        if (firstOpenReport) groupNotificationPublisher.postReported(post, reporterId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi báo cáo cho quản trị viên nhóm"));
    }

    @GetMapping("/group/{groupId}/reports")
    @Operation(summary = "Reported posts waiting for the group's administrators", description = "Only administrators and moderators; one entry per post with all its open reports")
    public ResponseEntity<ApiResponse<List<ReportedPostView>>> reportedPosts(@PathVariable UUID groupId) {
        UUID viewerId = currentUserId();
        groupAccessService.requireModerator(groupId);
        Map<UUID, List<GroupPostReport>> byPost = groupPostReportRepository
                .findByGroupIdAndStatusOrderByCreatedAtAsc(groupId, GroupReportStatus.OPEN).stream()
                .collect(Collectors.groupingBy(GroupPostReport::getPostId, LinkedHashMap::new, Collectors.toList()));
        List<ReportedPostView> views = new ArrayList<>();
        for (Map.Entry<UUID, List<GroupPostReport>> entry : byPost.entrySet()) {
            PostResponse post;
            try {
                post = postPresentationMapper.toResponse(getPostDetailQueryHandler.handle(
                        GetPostDetailQuery.builder().postId(entry.getKey()).viewerId(viewerId).build()));
            } catch (BusinessException e) {
                // Bài đã bị xóa/gỡ ở nơi khác: báo cáo không còn ý nghĩa
                groupPostReportRepository.resolveOpenReports(entry.getKey(), GroupReportStatus.OPEN, GroupReportStatus.ACTIONED, viewerId, LocalDateTime.now());
                continue;
            }
            List<GroupPostReport> reports = entry.getValue();
            List<ReasonCount> reasons = reports.stream()
                    .collect(Collectors.groupingBy(GroupPostReport::getReason, LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream().map(e -> new ReasonCount(e.getKey(), e.getValue())).toList();
            views.add(new ReportedPostView(post, reports.size(), reasons, reports.stream()
                    .map(r -> new ReportEntry(r.getReporterId(), r.getReason(), r.getDetail(), r.getCreatedAt())).toList()));
        }
        // Bài bị báo cáo nhiều nhất lên đầu
        views.sort(Comparator.comparingInt(ReportedPostView::reportCount).reversed());
        return ResponseEntity.ok(ApiResponse.success(views, "Reported group posts retrieved successfully"));
    }

    @PatchMapping("/group/{groupId}/reports/{postId}")
    @Operation(summary = "Resolve the reports of a post", description = "action=dismiss keeps the post; action=remove deletes it from the group (optional reason is sent to the author)")
    public ResponseEntity<ApiResponse<Void>> resolveReports(
            @PathVariable UUID groupId,
            @PathVariable UUID postId,
            @RequestParam String action,
            @RequestParam(required = false) String reason) {
        UUID moderatorId = currentUserId();
        groupAccessService.requireModerator(groupId);
        Post post = postRepository.findById(postId)
                .filter(p -> groupId.equals(p.getGroupId()))
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        switch (action.toLowerCase(Locale.ROOT)) {
            case "dismiss" -> groupPostReportRepository.resolveOpenReports(postId, GroupReportStatus.OPEN, GroupReportStatus.DISMISSED, moderatorId, LocalDateTime.now());
            case "remove" -> {
                if (!post.isDeleted()) groupPostModerationService.remove(post, moderatorId, reason);
                else groupPostReportRepository.resolveOpenReports(postId, GroupReportStatus.OPEN, GroupReportStatus.ACTIONED, moderatorId, LocalDateTime.now());
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        postCacheEvictor.evictAll();
        return ResponseEntity.ok(ApiResponse.success(null, "dismiss".equalsIgnoreCase(action) ? "Đã giữ lại bài viết" : "Đã gỡ bài viết khỏi nhóm"));
    }

    @GetMapping("/group/{groupId}/moderation-counts")
    @Operation(summary = "How many items wait for the group's administrators", description = "Pending posts, pending appeals and reported posts (for the badges of the admin tabs)")
    public ResponseEntity<ApiResponse<ModerationCounts>> moderationCounts(@PathVariable UUID groupId) {
        currentUserId();
        groupAccessService.requireModerator(groupId);
        ModerationCounts counts = new ModerationCounts(
                postRepository.countByGroupIdAndStatusAndDeletedFalse(groupId, PostStatus.PENDING_APPROVAL),
                postRepository.countByGroupIdAndAppealStatus(groupId, ModerationAppealStatus.PENDING),
                groupPostReportRepository.countReportedPosts(groupId, GroupReportStatus.OPEN));
        return ResponseEntity.ok(ApiResponse.success(counts, "Moderation counts retrieved successfully"));
    }

    private UUID currentUserId() {
        String id = jwtUtil.getCurrentUserId();
        if (id == null || id.isBlank()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return UUID.fromString(id);
    }
}
