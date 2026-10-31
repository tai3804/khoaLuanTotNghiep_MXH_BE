package iuh.fit.postservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostCommand;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostCommandHandler;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostResult;
import iuh.fit.postservice.application.features.post.commands.delete_post.DeletePostCommand;
import iuh.fit.postservice.application.features.post.commands.delete_post.DeletePostCommandHandler;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostCommand;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostCommandHandler;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostResult;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostCommand;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostCommandHandler;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostResult;
import iuh.fit.postservice.application.features.post.queries.get_all_posts.GetAllPostsQuery;
import iuh.fit.postservice.application.features.post.queries.get_all_posts.GetAllPostsQueryHandler;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailQuery;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailQueryHandler;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.features.post.queries.get_user_posts.GetUserPostsQuery;
import iuh.fit.postservice.application.features.post.queries.get_user_posts.GetUserPostsQueryHandler;
import iuh.fit.postservice.application.service.PostPublicationHelper;
import iuh.fit.postservice.application.service.PostSeenService;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.ModerationAppealStatus;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.event.ModerationAuditPublisher;
import iuh.fit.postservice.infrastructure.event.ReputationEventPublisher;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.constants.MessageConstants;
import iuh.fit.postservice.presentation.dto.request.CreatePostRequest;
import iuh.fit.postservice.presentation.dto.request.MarkPostsSeenRequest;
import iuh.fit.postservice.presentation.dto.request.SharePostRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostDateRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostRequest;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import iuh.fit.postservice.presentation.mapper.PostPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.POST_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Post Management", description = "APIs for creating, updating, deleting, sharing, and viewing posts")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    CreatePostCommandHandler createPostCommandHandler;
    UpdatePostCommandHandler updatePostCommandHandler;
    DeletePostCommandHandler deletePostCommandHandler;
    SharePostCommandHandler sharePostCommandHandler;
    GetPostDetailQueryHandler getPostDetailQueryHandler;
    GetUserPostsQueryHandler getUserPostsQueryHandler;
    GetAllPostsQueryHandler getAllPostsQueryHandler;
    PostSeenService postSeenService;
    PostVisibilityService postVisibilityService;
    PostPublicationHelper postPublicationHelper;
    PostCacheEvictor postCacheEvictor;
    PostPresentationMapper postPresentationMapper;
    JwtUtil jwtUtil;
    iuh.fit.postservice.infrastructure.persistence.repository.PostRepository postRepository;
    iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository postMediaRepository;
    iuh.fit.postservice.infrastructure.persistence.repository.SavedPostRepository savedPostRepository;
    iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository commentRepository;
    iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository reactionRepository;
    iuh.fit.postservice.application.mapper.PostFeatureMapper postFeatureMapper;
    iuh.fit.postservice.application.features.analytics.GetCreatorAnalyticsQueryHandler getCreatorAnalyticsQueryHandler;
    UserConnectionClient userConnectionClient;
    CacheManager cacheManager;
    StringRedisTemplate stringRedisTemplate;
    TransactionTemplate transactionTemplate;
    static final int MAX_GROUP_PINNED_POSTS = 5;
    KafkaTemplate<String, Object> kafkaTemplate;
    ReputationEventPublisher reputationEventPublisher;
    ModerationAuditPublisher moderationAuditPublisher;
    iuh.fit.postservice.application.service.GroupNotificationPublisher groupNotificationPublisher;
    iuh.fit.postservice.application.service.GroupPostModerationService groupPostModerationService;
    iuh.fit.postservice.application.features.post.queries.get_groups_feed.GetGroupsFeedQueryHandler getGroupsFeedQueryHandler;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a new post (with optional files)",
            description = "Creates a post with text content and optional media files uploaded to S3 via form-data",
            requestBody = @RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = {
                                    @SchemaProperty(name = "content", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "privacy", schema = @Schema(implementation = PostPrivacy.class)),
                                    @SchemaProperty(name = "allowedUserIds", schema = @Schema(type = "array", implementation = UUID.class)),
                                    @SchemaProperty(name = "groupId", schema = @Schema(type = "string", format = "uuid")),
                                    @SchemaProperty(name = "files", array = @ArraySchema(schema = @Schema(type = "string", format = "binary")))
                            }
                    )
            )
    )
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @ModelAttribute CreatePostRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        UUID currentUserId = getCurrentUserId();
        List<MultipartFile> validFiles = files != null ? files.stream().filter(f -> f != null && !f.isEmpty()).toList() : null;

        CreatePostCommand command = postPresentationMapper.toCreateCommand(request, validFiles, currentUserId);
        CreatePostResult result = createPostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_CREATED_SUCCESSFULLY));
    }

    @GetMapping
    @Operation(summary = "Get all posts", description = "Retrieves paginated list of posts using BaseFilter (keyword, filters map, page, size, sortBy, sortDirection) and optional cursor timestamp")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getAllPosts(
            @ParameterObject @Valid @ModelAttribute BaseFilter filter,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "unseenOnly", required = false, defaultValue = "false") boolean unseenOnly) {
        GetAllPostsQuery query = GetAllPostsQuery.builder()
                .filter(filter)
                .cursor(cursor)
                .viewerId(getOptionalCurrentUserId())
                .unseenOnly(unseenOnly)
                .build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @PostMapping("/seen")
    @Operation(summary = "Mark posts as seen", description = "Records that the authenticated user has viewed these posts on the feed, so GET /posts?unseenOnly=true skips them on later loads. Unknown post ids are ignored.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markPostsSeen(
            @Valid @org.springframework.web.bind.annotation.RequestBody MarkPostsSeenRequest request) {
        int recorded = postSeenService.markSeen(getCurrentUserId(), request.getPostIds());
        return ResponseEntity.ok(ApiResponse.success(Map.of("recorded", recorded), "Posts marked as seen"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts by keyword", description = "Retrieves paginated list of posts matching the keyword query")
    public ResponseEntity<ApiResponse<List<PostResponse>>> searchPosts(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        BaseFilter searchFilter = filter != null ? filter : new BaseFilter();
        searchFilter.setKeyword(query);
        GetAllPostsQuery searchQuery = GetAllPostsQuery.builder().filter(searchFilter).viewerId(getOptionalCurrentUserId()).build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(searchQuery);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/hashtag/{hashtag}")
    @Operation(summary = "Get posts by hashtag", description = "Retrieves paginated list of posts matching a given hashtag")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPostsByHashtag(
            @PathVariable("hashtag") String hashtag,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        String cleanTag = hashtag != null ? (hashtag.startsWith("#") ? hashtag.substring(1).trim().toLowerCase() : hashtag.trim().toLowerCase()) : "";
        BaseFilter hashtagFilter = filter != null ? filter : new BaseFilter();
        if (hashtagFilter.getFilters() == null) {
            hashtagFilter.setFilters(new HashMap<>());
        }
        hashtagFilter.getFilters().put("hashtag", cleanTag);
        GetAllPostsQuery query = GetAllPostsQuery.builder()
                .filter(hashtagFilter)
                .viewerId(getOptionalCurrentUserId())
                .build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Lấy danh sách bài viết theo hashtag thành công"));
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get group posts", description = "Retrieves only posts belonging to the specified community group")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getGroupPosts(
            @PathVariable UUID groupId,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        BaseFilter groupFilter = filter != null ? filter : new BaseFilter();
        if (groupFilter.getFilters() == null) groupFilter.setFilters(new HashMap<>());
        groupFilter.getFilters().put("groupId", groupId);
        groupFilter.getFilters().put("_groupFeed", true);
        GetAllPostsQuery query = GetAllPostsQuery.builder().filter(groupFilter).viewerId(getOptionalCurrentUserId()).build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.paged(postPresentationMapper.toPagedResponse(result), MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/groups/feed")
    @Operation(summary = "Feed of the groups I joined", description = "Newest posts of every group the current user belongs to. sort=activity (default) or newest")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getGroupsFeed(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "activity") String sort) {
        getCurrentUserId();
        PagedResponse<GetPostDetailResult> result = getGroupsFeedQueryHandler.handle(page, size, sort);
        return ResponseEntity.ok(ApiResponse.paged(postPresentationMapper.toPagedResponse(result), MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/group/{groupId}/media")
    @Operation(summary = "Photos and videos shared in a group", description = "Paged media of published group posts, newest first. type = all | image | video")
    public ResponseEntity<ApiResponse<List<iuh.fit.postservice.application.features.post.queries.group_media.GroupMediaItem>>> getGroupMedia(
            @PathVariable UUID groupId,
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "24") int size) {
        List<iuh.fit.postservice.domain.enums.MediaType> types = switch (type.toLowerCase()) {
            case "all" -> List.of(iuh.fit.postservice.domain.enums.MediaType.IMAGE, iuh.fit.postservice.domain.enums.MediaType.VIDEO);
            case "image" -> List.of(iuh.fit.postservice.domain.enums.MediaType.IMAGE);
            case "video" -> List.of(iuh.fit.postservice.domain.enums.MediaType.VIDEO);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
        requireGroupReadable(groupId);
        org.springframework.data.domain.Page<iuh.fit.postservice.application.features.post.queries.group_media.GroupMediaItem> result =
                postRepository.findGroupMedia(groupId, types,
                        org.springframework.data.domain.PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 60)));
        return ResponseEntity.ok(ApiResponse.paged(result.getContent(), result, "Group media retrieved successfully"));
    }

    public record GroupPostStats(long postsToday, long postsThisWeek, long totalPosts) { }

    @GetMapping("/group/{groupId}/stats")
    @Operation(summary = "Activity numbers of a group", description = "Posts today, posts in the last 7 days and total posts (published only)")
    public ResponseEntity<ApiResponse<GroupPostStats>> getGroupStats(@PathVariable UUID groupId) {
        requireGroupReadable(groupId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        GroupPostStats stats = new GroupPostStats(
                postRepository.countByGroupIdAndStatusAndDeletedFalseAndCreatedAtAfter(groupId, PostStatus.PUBLISHED, now.toLocalDate().atStartOfDay()),
                postRepository.countByGroupIdAndStatusAndDeletedFalseAndCreatedAtAfter(groupId, PostStatus.PUBLISHED, now.minusDays(7)),
                postRepository.countByGroupIdAndStatusAndDeletedFalse(groupId, PostStatus.PUBLISHED));
        return ResponseEntity.ok(ApiResponse.success(stats, "Group stats retrieved successfully"));
    }

    @GetMapping("/group/{groupId}/pending")
    @Operation(summary = "Get pending group posts", description = "Only group administrators and moderators can review pending posts")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPendingGroupPosts(@PathVariable UUID groupId) {
        UUID viewerId = getCurrentUserId();
        requireGroupModerator(groupId);
        List<PostResponse> posts = postRepository
                .findByGroupIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(groupId, PostStatus.PENDING_APPROVAL)
                .stream()
                .map(post -> getPostDetailQueryHandler.handle(GetPostDetailQuery.builder().postId(post.getId()).viewerId(viewerId).build()))
                .map(postPresentationMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(posts, "Pending group posts retrieved successfully"));
    }

    @GetMapping("/group/{groupId}/my-pending")
    @Operation(summary = "Get my own posts in the group that are waiting for approval")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getMyPendingGroupPosts(@PathVariable UUID groupId) {
        UUID viewerId = getCurrentUserId();
        List<PostResponse> posts = postRepository
                .findByGroupIdAndAuthorIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(groupId, viewerId, PostStatus.PENDING_APPROVAL)
                .stream()
                .map(post -> getPostDetailQueryHandler.handle(GetPostDetailQuery.builder().postId(post.getId()).viewerId(viewerId).build()))
                .map(postPresentationMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(posts, "My pending group posts retrieved successfully"));
    }

    @PostMapping("/{postId}/appeal")
    @Operation(summary = "Appeal a moderation decision on own post")
    public ResponseEntity<ApiResponse<Map<String, Object>>> appealModeratedPost(
            @PathVariable UUID postId, @org.springframework.web.bind.annotation.RequestBody Map<String, String> request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (!getCurrentUserId().equals(post.getAuthorId())) throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        if (post.getGroupId() != null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String message = request == null ? null : request.get("message");
        if (message == null || message.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT);
        if ((!post.isArchived() && !post.isDeleted())
                || post.getAppealStatus() == ModerationAppealStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        post.setAppealMessage(message.trim());
        post.setAppealStatus(ModerationAppealStatus.PENDING);
        post.setAppealReviewNote(null);
        postRepository.save(post);
        return ResponseEntity.ok(ApiResponse.success(appealView(post), "Appeal submitted successfully"));
    }

    @PostMapping("/{postId}/group-appeal")
    @Operation(summary = "Appeal a group moderation decision")
    public ResponseEntity<ApiResponse<Map<String, Object>>> appealGroupPost(
            @PathVariable UUID postId, @org.springframework.web.bind.annotation.RequestBody Map<String, String> request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null || !getCurrentUserId().equals(post.getAuthorId()) || !post.isDeleted()) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String message = request == null ? null : request.get("message");
        if (message == null || message.isBlank() || post.getAppealStatus() == ModerationAppealStatus.PENDING) throw new BusinessException(ErrorCode.INVALID_INPUT);
        post.setAppealMessage(message.trim()); post.setAppealStatus(ModerationAppealStatus.PENDING); post.setAppealReviewNote(null);
        postRepository.save(post);
        return ResponseEntity.ok(ApiResponse.success(appealView(post), "Group appeal submitted successfully"));
    }

    @GetMapping("/group/{groupId}/my-appeals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myGroupAppeals(@PathVariable UUID groupId) {
        UUID userId = getCurrentUserId();
        List<Map<String, Object>> items = postRepository.findByGroupIdAndAuthorIdAndDeletedTrueAndModerationActionIsNotNullOrderByUpdatedAtDesc(groupId, userId)
                .stream().map(this::appealView).toList();
        return ResponseEntity.ok(ApiResponse.success(items, "My group appeals retrieved successfully"));
    }

    @GetMapping("/group/{groupId}/appeals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> groupAppeals(@PathVariable UUID groupId) {
        requireGroupModerator(groupId);
        return ResponseEntity.ok(ApiResponse.success(postRepository.findByGroupIdAndAppealStatusOrderByUpdatedAtDesc(groupId, ModerationAppealStatus.PENDING).stream().map(this::appealView).toList(), "Group appeals retrieved successfully"));
    }

    @PatchMapping("/{postId}/group-appeal/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewGroupAppeal(@PathVariable UUID postId, @RequestParam boolean approved, @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> request) {
        UUID reviewerId = getCurrentUserId();
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null || post.getAppealStatus() != ModerationAppealStatus.PENDING) throw new BusinessException(ErrorCode.INVALID_INPUT);
        requireGroupModerator(post.getGroupId());
        post.setAppealStatus(approved ? ModerationAppealStatus.ACCEPTED : ModerationAppealStatus.REJECTED);
        post.setAppealReviewNote(request == null ? null : request.get("note"));
        if (approved) { post.setDeleted(false); post.setArchived(false); post.setStatus(PostStatus.PUBLISHED); }
        postRepository.save(post); clearPostCaches(); sendGroupAppealDecisionNotification(post, reviewerId, approved);
        return ResponseEntity.ok(ApiResponse.success(appealView(post), approved ? "Group appeal accepted" : "Group appeal rejected"));
    }

    @GetMapping("/moderation-appeals/mine")
    @Operation(summary = "Get current user's moderation appeals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myModerationAppeals() {
        UUID userId = getCurrentUserId();
        List<Map<String, Object>> appeals = postRepository.findAll().stream()
                .filter(post -> post.getGroupId() == null && userId.equals(post.getAuthorId()) && (post.getModerationAction() != null
                        || post.isArchived() || post.isDeleted() || post.getAppealStatus() != null))
                .sorted(java.util.Comparator.comparing(Post::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::appealView).toList();
        return ResponseEntity.ok(ApiResponse.success(appeals, "Moderation appeals retrieved successfully"));
    }

    @GetMapping("/moderation-appeals")
    @Operation(summary = "Get pending moderation appeals for moderators")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> moderationAppeals() {
        requireSystemModerator();
        return ResponseEntity.ok(ApiResponse.success(postRepository.findByAppealStatusOrderByUpdatedAtDesc(ModerationAppealStatus.PENDING)
                .stream().filter(post -> post.getGroupId() == null).map(this::appealView).toList(), "Pending moderation appeals retrieved successfully"));
    }

    @PatchMapping("/{postId}/appeal/review")
    @Operation(summary = "Accept or reject a post moderation appeal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewModerationAppeal(
            @PathVariable UUID postId, @RequestParam boolean approved, @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> request) {
        UUID moderatorId = getCurrentUserId();
        requireSystemModerator();
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getAppealStatus() != ModerationAppealStatus.PENDING) throw new BusinessException(ErrorCode.INVALID_INPUT);
        post.setAppealStatus(approved ? ModerationAppealStatus.ACCEPTED : ModerationAppealStatus.REJECTED);
        post.setAppealReviewNote(request == null ? null : request.get("note"));
        if (approved) { post.setDeleted(false); post.setArchived(false); post.setStatus(PostStatus.PUBLISHED); }
        postRepository.save(post);
        clearPostCaches();
        if (approved) reputationEventPublisher.revert(post.getAuthorId(), post.getId(), "Kháng nghị được chấp nhận");
        moderationAuditPublisher.publishAppealReviewed(post.getId(), moderatorId, approved, post.getAppealReviewNote());
        sendAppealDecisionNotification(post, moderatorId, approved);
        return ResponseEntity.ok(ApiResponse.success(appealView(post), approved ? "Appeal accepted and post restored" : "Appeal rejected"));
    }

    @PatchMapping("/{postId}/group-review")
    @Operation(summary = "Approve or reject a pending group post", description = "Only group administrators and moderators can perform this action")
    public ResponseEntity<ApiResponse<PostResponse>> reviewGroupPost(
            @PathVariable UUID postId,
            @RequestParam boolean approved) {
        UUID viewerId = getCurrentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        requireGroupModerator(post.getGroupId());
        if (post.getStatus() != PostStatus.PENDING_APPROVAL) throw new BusinessException(ErrorCode.INVALID_INPUT);

        PostStatus targetStatus = (post.getScheduledPublishAt() != null && post.getScheduledPublishAt().isAfter(java.time.Instant.now()))
                ? PostStatus.SCHEDULED
                : PostStatus.PUBLISHED;
        post.setStatus(approved ? targetStatus : PostStatus.REJECTED);
        if (approved) post.setLastActivityAt(java.time.LocalDateTime.now());
        Post saved = postRepository.save(post);
        clearPostCaches();
        groupNotificationPublisher.postReviewed(saved, viewerId, approved);
        if (approved && targetStatus == PostStatus.PUBLISHED) {
            postPublicationHelper.publishAndNotifyDeferredPost(saved);
        }

        PostResponse response = approved
                ? postPresentationMapper.toResponse(getPostDetailQueryHandler.handle(GetPostDetailQuery.builder().postId(postId).viewerId(viewerId).build()))
                : PostResponse.builder().id(saved.getId()).authorId(saved.getAuthorId()).groupId(saved.getGroupId())
                .content(saved.getContent()).status(saved.getStatus()).createdAt(saved.getCreatedAt()).build();
        return ResponseEntity.ok(ApiResponse.success(response, approved ? "Post approved successfully" : "Post rejected successfully"));
    }

    @PatchMapping("/{postId}/group-pin")
    @Operation(summary = "Pin or unpin a post at the top of its group", description = "Only group administrators and moderators; at most 5 pinned posts per group")
    public ResponseEntity<ApiResponse<PostResponse>> pinGroupPost(
            @PathVariable UUID postId,
            @RequestParam(value = "pinned", defaultValue = "true") boolean pinned) {
        UUID viewerId = getCurrentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null || post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException(PostServiceErrorCode.GROUP_POST_NOT_PINNABLE);
        }
        requireGroupModerator(post.getGroupId());
        if (pinned) {
            if (post.getGroupPinnedAt() == null) {
                if (postRepository.countByGroupIdAndGroupPinnedAtIsNotNullAndDeletedFalse(post.getGroupId()) >= MAX_GROUP_PINNED_POSTS) {
                    throw new BusinessException(PostServiceErrorCode.GROUP_PIN_LIMIT);
                }
                post.setGroupPinnedAt(java.time.LocalDateTime.now());
            }
        } else {
            post.setGroupPinnedAt(null);
        }
        postRepository.save(post);
        clearPostCaches();
        GetPostDetailResult result = getPostDetailQueryHandler.handle(GetPostDetailQuery.builder().postId(postId).viewerId(viewerId).build());
        return ResponseEntity.ok(ApiResponse.success(postPresentationMapper.toResponse(result), pinned ? "Post pinned to the group" : "Post unpinned"));
    }

    @DeleteMapping("/{postId}/group-moderation")
    @Operation(summary = "Remove a group post", description = "Only an administrator or moderator of that group can remove a group post")
    public ResponseEntity<ApiResponse<Void>> removeGroupPost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason) {
        UUID moderatorId = getCurrentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        requireGroupModerator(post.getGroupId());

        groupPostModerationService.remove(post, moderatorId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Group post removed successfully"));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post details", description = "Retrieves detail information of a specific post by ID")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable("postId") UUID postId) {
        GetPostDetailQuery query = GetPostDetailQuery.builder().postId(postId).viewerId(getOptionalCurrentUserId()).build();
        GetPostDetailResult result = getPostDetailQueryHandler.handle(query);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_RETRIEVED_SUCCESSFULLY));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update post", description = "Updates content or privacy of an existing post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable("postId") UUID postId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdatePostRequest request) {
        UUID currentUserId = getCurrentUserId();
        UpdatePostCommand command = postPresentationMapper.toUpdateCommand(request, postId, currentUserId);
        UpdatePostResult result = updatePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_UPDATED_SUCCESSFULLY));
    }

    @PatchMapping("/{postId}/pin")
    @Operation(summary = "Pin or unpin post", description = "Toggles or sets the pinned status of a post")
    public ResponseEntity<ApiResponse<PostResponse>> togglePinPost(
            @PathVariable("postId") UUID postId,
            @RequestParam(value = "isPinned", required = false) Boolean isPinned) {
        UUID currentUserId = getCurrentUserId();
        UpdatePostCommand command = UpdatePostCommand.builder()
                .postId(postId)
                .userId(currentUserId)
                .isPinned(isPinned != null ? isPinned : true)
                .build();
        UpdatePostResult result = updatePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "Post pin status updated successfully"));
    }

    @PatchMapping("/{postId}/archive")
    @Operation(summary = "Archive or unarchive post", description = "Toggles or sets the archived status of a post")
    public ResponseEntity<ApiResponse<PostResponse>> toggleArchivePost(
            @PathVariable("postId") UUID postId,
            @RequestParam(value = "isArchived", required = false) Boolean isArchived) {
        UUID currentUserId = getCurrentUserId();
        UpdatePostCommand command = UpdatePostCommand.builder()
                .postId(postId)
                .userId(currentUserId)
                .isArchived(isArchived != null ? isArchived : true)
                .build();
        UpdatePostResult result = updatePostCommandHandler.handle(command);
        clearPostCaches();
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "Post archive status updated successfully"));
    }

    @GetMapping("/user/{userId}/archived")
    @Operation(summary = "Get user archived posts", description = "Retrieves paginated list of archived posts for the owner")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getUserArchivedPosts(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UUID currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt")));
        org.springframework.data.domain.Page<Post> postsPage = postRepository.findByAuthorIdAndDeletedFalseAndIsArchivedTrue(userId, pageable);
        List<GetPostDetailResult> content = postsPage.getContent().stream().map(post -> {
            List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
            return postFeatureMapper.toGetDetailResult(post, mediaList);
        }).toList();
        PagedResponse<GetPostDetailResult> pagedResult = postFeatureMapper.toPagedResponse(postsPage, content);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(pagedResult);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Archived posts retrieved successfully"));
    }

    @PatchMapping("/{postId}/privacy")
    @Operation(summary = "Update post privacy", description = "Updates audience/privacy level of a post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostPrivacy(
            @PathVariable("postId") UUID postId,
            @RequestParam("privacy") PostPrivacy privacy) {
        UUID currentUserId = getCurrentUserId();
        UpdatePostCommand command = UpdatePostCommand.builder()
                .postId(postId)
                .userId(currentUserId)
                .privacy(privacy)
                .build();
        UpdatePostResult result = updatePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "Post privacy updated successfully"));
    }

    @PatchMapping("/{postId}/date")
    @Operation(summary = "Update post date", description = "Updates the display date of the authenticated user's post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostDate(
            @PathVariable("postId") UUID postId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdatePostDateRequest request) {
        UUID currentUserId = getCurrentUserId();
        int updated = postRepository.updateCreatedAtByIdAndAuthorId(postId, currentUserId, request.getCreatedAt());
        if (updated == 0) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }
        clearPostCaches();
        GetPostDetailResult result = getPostDetailQueryHandler.handle(GetPostDetailQuery.builder().postId(postId).viewerId(currentUserId).build());
        return ResponseEntity.ok(ApiResponse.success(postPresentationMapper.toResponse(result), "Post date updated successfully"));
    }

    @PatchMapping("/privacy/batch")
    @Operation(summary = "Batch update privacy for author posts", description = "Updates audience/privacy level for all existing posts of current user")
    public ResponseEntity<ApiResponse<Integer>> updateBatchPrivacy(
            @RequestParam("privacy") PostPrivacy privacy) {
        UUID currentUserId = getCurrentUserId();
        int count = postRepository.updatePrivacyByAuthorId(currentUserId, privacy);
        clearPostCaches();
        return ResponseEntity.ok(ApiResponse.success(count, "Đã cập nhật đối tượng cho " + count + " bài viết thành công"));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete post", description = "Deletes a post and cleans up associated media files")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable("postId") UUID postId) {
        UUID currentUserId = getCurrentUserId();
        DeletePostCommand command = DeletePostCommand.builder().postId(postId).userId(currentUserId).build();
        deletePostCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.POST_DELETED_SUCCESSFULLY));
    }

    @PostMapping(value = {"/{postId}/share", "/share"})
    @Operation(summary = "Share post", description = "Shares an existing post as a new post")
    public ResponseEntity<ApiResponse<PostResponse>> sharePost(
            @PathVariable(value = "postId", required = false) UUID postId,
            @Valid @org.springframework.web.bind.annotation.RequestBody SharePostRequest request) {
        UUID currentUserId = getCurrentUserId();
        UUID targetPostId = postId != null ? postId : (request.getOriginalPostId() != null ? request.getOriginalPostId() : request.getSharedPostId());
        if (targetPostId == null) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }
        SharePostCommand command = postPresentationMapper.toShareCommand(request, targetPostId, currentUserId);
        SharePostResult result = sharePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_SHARED_SUCCESSFULLY));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user posts", description = "Retrieves paginated list of posts created by a specific user")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getUserPosts(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        GetUserPostsQuery query = GetUserPostsQuery.builder().userId(userId).viewerId(getOptionalCurrentUserId()).page(page).size(size).build();
        PagedResponse<GetPostDetailResult> result = getUserPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.USER_POSTS_RETRIEVED_SUCCESSFULLY));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return UUID.fromString(userIdStr);
    }

    private UUID getOptionalCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null || userIdStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (Exception e) {
            return null;
        }
    }

    private void requireGroupModerator(UUID groupId) {
        try {
            var response = userConnectionClient.getGroupFeedVisibility(List.of(groupId));
            var membership = response == null || response.getData() == null ? null : response.getData().stream()
                    .filter(item -> groupId.equals(item.groupId()) && item.member())
                    .findFirst().orElse(null);
            if (membership == null || !("ADMIN".equals(membership.role()) || "MODERATOR".equals(membership.role()))) {
                throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }
    }

    /** Nhóm riêng tư chỉ cho thành viên xem nội dung (nhóm công khai ai cũng xem được). */
    private void requireGroupReadable(UUID groupId) {
        UserConnectionClient.GroupFeedVisibility visibility;
        try {
            var response = userConnectionClient.getGroupFeedVisibility(List.of(groupId));
            visibility = response == null || response.getData() == null ? null : response.getData().stream()
                    .filter(item -> groupId.equals(item.groupId())).findFirst().orElse(null);
        } catch (Exception exception) {
            throw new BusinessException(PostServiceErrorCode.GROUP_SERVICE_UNAVAILABLE);
        }
        if (visibility == null || (!visibility.member() && !visibility.publicGroup())) {
            throw new BusinessException(PostServiceErrorCode.GROUP_PRIVATE);
        }
    }

    private void requireSystemModerator() {
        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean allowed = roles != null && roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role)
                || "MODERATOR".equalsIgnoreCase(role) || "ROLE_MODERATOR".equalsIgnoreCase(role));
        if (!allowed) throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
    }

    private Map<String, Object> appealView(Post post) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("postId", post.getId()); view.put("authorId", post.getAuthorId()); view.put("content", post.getContent());
        view.put("createdAt", post.getCreatedAt()); view.put("likeCount", post.getLikeCount());
        view.put("commentCount", post.getCommentCount()); view.put("shareCount", post.getShareCount());
        view.put("action", post.getModerationAction() == null ? (post.isArchived() ? "HIDE_POST" : "DELETE_POST") : post.getModerationAction());
        view.put("reason", post.getModerationReason() == null ? "Nội dung đã bị kiểm duyệt" : post.getModerationReason());
        view.put("appealMessage", post.getAppealMessage()); view.put("appealStatus", post.getAppealStatus());
        view.put("reviewNote", post.getAppealReviewNote()); view.put("updatedAt", post.getUpdatedAt());
        return view;
    }

    private void sendAppealDecisionNotification(Post post, UUID moderatorId, boolean approved) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientId", post.getAuthorId().toString()); notification.put("actorId", moderatorId.toString());
        notification.put("type", "SYSTEM"); notification.put("title", approved ? "Kháng nghị đã được chấp nhận" : "Kháng nghị đã được xem xét");
        notification.put("content", approved ? "Bài viết của bạn đã được khôi phục." : "Kháng nghị không được chấp nhận" + (post.getAppealReviewNote() == null ? "." : ": " + post.getAppealReviewNote()));
        notification.put("targetId", post.getId().toString()); notification.put("targetUrl", "/support-inbox?postId=" + post.getId()); notification.put("avatarUrl", null);
        kafkaTemplate.send("notification.in-app.send", notification);
    }

    private void sendGroupAppealDecisionNotification(Post post, UUID reviewerId, boolean approved) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientId", post.getAuthorId().toString()); notification.put("actorId", reviewerId.toString());
        notification.put("type", "SYSTEM"); notification.put("title", approved ? "Kháng nghị nhóm đã được chấp nhận" : "Kháng nghị nhóm đã được xem xét");
        notification.put("content", approved ? "Bài viết của bạn trong nhóm đã được khôi phục." : "Kháng nghị không được chấp nhận" + (post.getAppealReviewNote() == null ? "." : ": " + post.getAppealReviewNote()));
        notification.put("targetId", post.getId().toString()); notification.put("targetUrl", "/groups/" + post.getGroupId() + "?tab=appeals"); notification.put("avatarUrl", null);
        kafkaTemplate.send("notification.in-app.send", notification);
    }

    private void clearPostCaches() {
        postCacheEvictor.evictAllWithRecheck();
    }

    @PostMapping("/{postId}/view")
    @Operation(summary = "Record post view", description = "Increments view count of a post with per-viewer deduplication")
    public ResponseEntity<ApiResponse<Long>> recordPostView(@PathVariable("postId") UUID postId) {
        UUID viewerId = getOptionalCurrentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (post.getStatus() != PostStatus.PUBLISHED
                || post.isArchived()
                || !postVisibilityService.canView(post, viewerId)) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        long currentViews = post.getViewCount();
        if (viewerId != null && !viewerId.equals(post.getAuthorId())) {
            boolean shouldIncrement = true;
            try {
                String dedupKey = "post-service:post-view:" + postId + ":" + viewerId;
                Boolean added = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(6));
                shouldIncrement = Boolean.TRUE.equals(added);
            } catch (Exception ignored) {
            }
            if (shouldIncrement) {
                postRepository.incrementViewCount(postId);
                currentViews += 1;
            }
        }
        return ResponseEntity.ok(ApiResponse.success(currentViews, "Ghi nhận lượt xem bài viết"));
    }

    @GetMapping("/analytics/me")
    @Operation(summary = "Get creator post analytics", description = "Retrieves overview and trend metrics for current creator's posts")
    public ResponseEntity<ApiResponse<iuh.fit.postservice.presentation.dto.response.analytics.PostAnalyticsResponse>> getCreatorAnalytics(
            @RequestParam(value = "period", defaultValue = "28d") String period) {
        UUID currentUserId = getCurrentUserId();
        var response = getCreatorAnalyticsQueryHandler.handle(currentUserId, period);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy số liệu phân tích người sáng tạo thành công"));
    }

    @GetMapping("/{postId}/insights")
    @Operation(summary = "Get post insights", description = "Retrieves Facebook-style performance insights for a post")
    public ResponseEntity<ApiResponse<iuh.fit.postservice.presentation.dto.response.PostInsightsResponse>> getPostInsights(@PathVariable("postId") UUID postId) {
        UUID currentUserId = getCurrentUserId();
        var post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        long views = post.getViewCount();
        long likes = post.getLikeCount();
        long comments = post.getCommentCount();
        long shares = post.getShareCount();
        long saves = savedPostRepository.countByPostId(postId);

        long totalInteractions = likes + comments + shares + saves;
        long reach = Math.max(views, totalInteractions + (views > 0 ? (long)(views * 0.85) : 0L));
        double engagementRate = views <= 0 ? (totalInteractions > 0 ? 100.0 : 0.0) : Math.min(100.0, Math.round((totalInteractions * 1000.0 / views)) / 10.0);

        var mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(postId);
        boolean hasVideo = mediaList.stream().anyMatch(m -> m.getMediaType() == iuh.fit.postservice.domain.enums.MediaType.VIDEO);
        double avgWatchRetention = hasVideo ? Math.min(95.0, Math.max(45.0, 50.0 + engagementRate * 2.5)) : 0.0;

        boolean isGroup = post.getGroupId() != null;
        long shareTraffic = shares * 4L;
        long directViews = Math.max(1L, (long)(views * (isGroup ? 0.25 : 0.65)));
        long profileViews = Math.max(0L, (long)(views * 0.15));
        long exploreViews = Math.max(0L, views - directViews - profileViews - shareTraffic);
        long sumTraffic = Math.max(1L, directViews + profileViews + exploreViews + shareTraffic);

        java.util.Map<String, Double> trafficSources = new java.util.LinkedHashMap<>();
        if (isGroup) {
            trafficSources.put("Nhóm / Cộng đồng", Math.round((directViews * 1000.0) / sumTraffic) / 10.0);
        } else {
            trafficSources.put("Bảng tin người theo dõi", Math.round((directViews * 1000.0) / sumTraffic) / 10.0);
        }
        trafficSources.put("Trang cá nhân", Math.round((profileViews * 1000.0) / sumTraffic) / 10.0);
        trafficSources.put("Khám phá / Đề xuất", Math.round((exploreViews * 1000.0) / sumTraffic) / 10.0);
        trafficSources.put("Chia sẻ & Liên kết", Math.max(0.0, Math.round((shareTraffic * 1000.0) / sumTraffic) / 10.0));

        java.util.Map<String, Double> audienceGender = java.util.Collections.emptyMap();

        List<iuh.fit.postservice.presentation.dto.response.PostInsightsResponse.HourlyStat> hourly = new java.util.ArrayList<>();
        String[] hours = {"00:00", "04:00", "08:00", "12:00", "16:00", "20:00"};
        int postCreatedHour = post.getCreatedAt() != null ? post.getCreatedAt().getHour() : 12;
        for (int i = 0; i < hours.length; i++) {
            int blockHour = i * 4;
            double hourWeight = 1.0 / (1.0 + Math.abs(blockHour - postCreatedHour) * 0.25);
            long hViews = Math.round(views * (hourWeight / 3.5));
            long hEngagements = Math.round((likes + comments) * (hourWeight / 3.5));
            hourly.add(iuh.fit.postservice.presentation.dto.response.PostInsightsResponse.HourlyStat.builder()
                    .hour(hours[i])
                    .views(hViews)
                    .engagements(hEngagements)
                    .build());
        }

        String snippet = post.getContent();
        if (snippet != null && snippet.length() > 80) {
            snippet = snippet.substring(0, 80) + "...";
        }

        var response = iuh.fit.postservice.presentation.dto.response.PostInsightsResponse.builder()
                .postId(post.getId())
                .contentSnippet(snippet)
                .publishedAt(post.getCreatedAt())
                .views(views)
                .reach(reach)
                .likes(likes)
                .comments(comments)
                .shares(shares)
                .saves(saves)
                .engagementRate(engagementRate)
                .avgWatchRetention(avgWatchRetention)
                .trafficSources(trafficSources)
                .audienceGender(audienceGender)
                .hourlyViews(hourly)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin chi tiết bài viết thành công"));
    }

    @GetMapping("/scheduled/me")
    @Operation(summary = "Get user scheduled posts", description = "Retrieves posts scheduled for publication by current user")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getScheduledPosts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UUID currentUserId = getCurrentUserId();
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var pagedPosts = postRepository.findByAuthorIdAndStatusAndDeletedFalseOrderByScheduledPublishAtAsc(
                currentUserId, PostStatus.SCHEDULED, pageable
        );

        List<PostResponse> responses = pagedPosts.getContent().stream()
                .map(post -> {
                    var media = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
                    var detail = postFeatureMapper.toGetDetailResult(post, media);
                    return postPresentationMapper.toResponse(detail);
                })
                .toList();

        var paged = iuh.fit.commonframework.application.dto.PagedResponse.<PostResponse>builder()
                .content(responses)
                .page(pagedPosts.getNumber())
                .size(pagedPosts.getSize())
                .totalElements(pagedPosts.getTotalElements())
                .totalPages(pagedPosts.getTotalPages())
                .last(pagedPosts.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.paged(paged, "Lấy danh sách bài viết đã lên lịch thành công"));
    }

    @PostMapping("/{postId}/publish-now")
    @Operation(summary = "Publish scheduled post immediately", description = "Immediately sets scheduled post status to PUBLISHED")
    public ResponseEntity<ApiResponse<PostResponse>> publishScheduledPostNow(@PathVariable("postId") UUID postId) {
        UUID currentUserId = getCurrentUserId();
        var post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        if (post.getStatus() != PostStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        post.setStatus(PostStatus.PUBLISHED);
        post.setLastActivityAt(java.time.LocalDateTime.now());
        Post saved = postRepository.save(post);
        clearPostCaches();
        postPublicationHelper.publishAndNotifyDeferredPost(saved);

        var media = postMediaRepository.findByPostIdOrderBySortOrderAsc(saved.getId());
        var detail = postFeatureMapper.toGetDetailResult(saved, media);
        detail.setPoll(iuh.fit.postservice.application.util.PostPollHelper.toResponse(saved.getPollData(), currentUserId));
        var response = postPresentationMapper.toResponse(detail);

        return ResponseEntity.ok(ApiResponse.success(response, "Đăng bài viết ngay thành công"));
    }

    @PostMapping("/{postId}/poll/vote")
    @Operation(summary = "Vote in a post poll", description = "Cast a vote on one or more options in a post poll")
    public ResponseEntity<ApiResponse<PostResponse>> votePoll(
            @PathVariable("postId") UUID postId,
            @Valid @org.springframework.web.bind.annotation.RequestBody iuh.fit.postservice.presentation.dto.request.VotePollRequest request
    ) {
        UUID currentUserId = getCurrentUserId();

        Post viewCheckPost = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));
        if (viewCheckPost.getStatus() != PostStatus.PUBLISHED
                || viewCheckPost.isArchived()
                || !postVisibilityService.canView(viewCheckPost, currentUserId)) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        Post updatedPost = transactionTemplate.execute(status -> {
            Post post = postRepository.findByIdAndDeletedFalseForUpdate(postId)
                    .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

            if (post.getStatus() != PostStatus.PUBLISHED || post.isArchived()) {
                throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
            }

            if (post.getPollData() == null || post.getPollData().isBlank()) {
                throw new BusinessException(PostServiceErrorCode.POLL_NOT_FOUND);
            }

            iuh.fit.postservice.domain.entities.PostPoll poll = iuh.fit.postservice.application.util.PostPollHelper.fromJson(post.getPollData());
            if (poll == null) {
                throw new BusinessException(PostServiceErrorCode.POLL_NOT_FOUND);
            }

            boolean isExpired = poll.getClosedAt() != null && java.time.Instant.now().isAfter(poll.getClosedAt());
            if (poll.isClosed() || isExpired) {
                throw new BusinessException(PostServiceErrorCode.POLL_CLOSED);
            }

            Set<String> validOptionIds = new java.util.HashSet<>();
            for (var opt : poll.getOptions()) {
                validOptionIds.add(opt.getId());
            }

            List<String> selectedOptions = request.getOptionIds().stream()
                    .filter(validOptionIds::contains)
                    .toList();

            if (selectedOptions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            if (!poll.isMultipleChoice() && selectedOptions.size() > 1) {
                selectedOptions = List.of(selectedOptions.get(0));
            }

            for (var opt : poll.getOptions()) {
                if (opt.getVoterIds() == null) {
                    opt.setVoterIds(new java.util.HashSet<>());
                }
                if (!poll.isMultipleChoice()) {
                    opt.getVoterIds().remove(currentUserId);
                }
                if (selectedOptions.contains(opt.getId())) {
                    opt.getVoterIds().add(currentUserId);
                }
                opt.setVoteCount(opt.getVoterIds().size());
            }

            post.setPollData(iuh.fit.postservice.application.util.PostPollHelper.toJson(poll));
            return postRepository.save(post);
        });

        clearPostCaches();

        var media = postMediaRepository.findByPostIdOrderBySortOrderAsc(updatedPost.getId());
        var detail = postFeatureMapper.toGetDetailResult(updatedPost, media);
        detail.setPoll(iuh.fit.postservice.application.util.PostPollHelper.toResponse(updatedPost.getPollData(), currentUserId));
        var response = postPresentationMapper.toResponse(detail);
        response.setPoll(detail.getPoll());

        return ResponseEntity.ok(ApiResponse.success(response, "Bình chọn thành công"));
    }
}
