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
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.constants.MessageConstants;
import iuh.fit.postservice.presentation.dto.request.CreatePostRequest;
import iuh.fit.postservice.presentation.dto.request.SharePostRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostDateRequest;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import iuh.fit.postservice.presentation.mapper.PostPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import iuh.fit.postservice.domain.enums.ModerationAppealStatus;

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
    PostPresentationMapper postPresentationMapper;
    JwtUtil jwtUtil;
    iuh.fit.postservice.infrastructure.persistence.repository.PostRepository postRepository;
    UserConnectionClient userConnectionClient;
    CacheManager cacheManager;
    KafkaTemplate<String, Object> kafkaTemplate;

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
            @RequestParam(value = "cursor", required = false) String cursor) {
        GetAllPostsQuery query = GetAllPostsQuery.builder()
                .filter(filter)
                .cursor(cursor)
                .viewerId(getCurrentUserId())
                .build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts by keyword", description = "Retrieves paginated list of posts matching the keyword query")
    public ResponseEntity<ApiResponse<List<PostResponse>>> searchPosts(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        BaseFilter searchFilter = filter != null ? filter : new BaseFilter();
        searchFilter.setKeyword(query);
        GetAllPostsQuery searchQuery = GetAllPostsQuery.builder().filter(searchFilter).viewerId(getCurrentUserId()).build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(searchQuery);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
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
        GetAllPostsQuery query = GetAllPostsQuery.builder().filter(groupFilter).viewerId(getCurrentUserId()).build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.paged(postPresentationMapper.toPagedResponse(result), MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
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

    @PostMapping("/{postId}/appeal")
    @Operation(summary = "Appeal a moderation decision on own post")
    public ResponseEntity<ApiResponse<Map<String, Object>>> appealModeratedPost(
            @PathVariable UUID postId, @org.springframework.web.bind.annotation.RequestBody Map<String, String> request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
        if (!getCurrentUserId().equals(post.getAuthorId())) throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.UNAUTHORIZED_ACTION);
        if (post.getGroupId() != null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        String message = request == null ? null : request.get("message");
        if (message == null || message.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT);
        // Historical moderation records may not have an action value. The post's
        // hidden/deleted state is still a valid moderation decision to appeal.
        if ((!post.isArchived() && !post.isDeleted() && post.getModerationAction() == null)
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
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
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
        List<Map<String, Object>> items = postRepository.findAll().stream().filter(post -> groupId.equals(post.getGroupId()) && userId.equals(post.getAuthorId()) && post.isDeleted() && post.getModerationAction() != null).map(this::appealView).toList();
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
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
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
                // Include historical posts that were hidden/deleted before appeal
                // metadata was introduced, so their owners can still appeal.
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
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getAppealStatus() != ModerationAppealStatus.PENDING) throw new BusinessException(ErrorCode.INVALID_INPUT);
        post.setAppealStatus(approved ? ModerationAppealStatus.ACCEPTED : ModerationAppealStatus.REJECTED);
        post.setAppealReviewNote(request == null ? null : request.get("note"));
        if (approved) { post.setDeleted(false); post.setArchived(false); post.setStatus(PostStatus.PUBLISHED); }
        postRepository.save(post);
        clearPostCaches();
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
                .orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        requireGroupModerator(post.getGroupId());
        if (post.getStatus() != PostStatus.PENDING_APPROVAL) throw new BusinessException(ErrorCode.INVALID_INPUT);

        post.setStatus(approved ? PostStatus.PUBLISHED : PostStatus.REJECTED);
        postRepository.save(post);
        clearPostCaches();
        GetPostDetailResult result = getPostDetailQueryHandler.handle(
                GetPostDetailQuery.builder().postId(postId).viewerId(viewerId).build());
        return ResponseEntity.ok(ApiResponse.success(postPresentationMapper.toResponse(result),
                approved ? "Post approved successfully" : "Post rejected successfully"));
    }

    @DeleteMapping("/{postId}/group-moderation")
    @Operation(summary = "Remove a group post", description = "Only an administrator or moderator of that group can remove a group post")
    public ResponseEntity<ApiResponse<Void>> removeGroupPost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason) {
        UUID moderatorId = getCurrentUserId();
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND));
        if (post.getGroupId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        requireGroupModerator(post.getGroupId());

        post.setDeleted(true);
        post.setArchived(true);
        post.setStatus(PostStatus.REJECTED);
        post.setModeratedBy(moderatorId);
        post.setModerationAction("DELETE_POST");
        post.setModerationReason(reason == null || reason.isBlank()
                ? "Bài viết không phù hợp với quy định của nhóm."
                : reason.trim());
        post.setAppealStatus(null);
        post.setAppealMessage(null);
        post.setAppealReviewNote(null);
        postRepository.save(post);
        clearPostCaches();
        sendGroupPostRemovalNotification(post, moderatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Group post removed successfully"));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post details", description = "Retrieves detail information of a specific post by ID")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable("postId") UUID postId) {
        GetPostDetailQuery query = GetPostDetailQuery.builder().postId(postId).viewerId(getCurrentUserId()).build();
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
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "Post archive status updated successfully"));
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
            throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.UNAUTHORIZED_ACTION);
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
            throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.POST_NOT_FOUND);
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
        GetUserPostsQuery query = GetUserPostsQuery.builder().userId(userId).viewerId(getCurrentUserId()).page(page).size(size).build();
        PagedResponse<GetPostDetailResult> result = getUserPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.USER_POSTS_RETRIEVED_SUCCESSFULLY));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }

    private void requireGroupModerator(UUID groupId) {
        try {
            var response = userConnectionClient.getGroupFeedVisibility(List.of(groupId));
            var membership = response == null || response.getData() == null ? null : response.getData().stream()
                    .filter(item -> groupId.equals(item.groupId()) && item.member())
                    .findFirst().orElse(null);
            if (membership == null || !("ADMIN".equals(membership.role()) || "MODERATOR".equals(membership.role()))) {
                throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.UNAUTHORIZED_ACTION);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }
    }

    private void requireSystemModerator() {
        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean allowed = roles != null && roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role)
                || "MODERATOR".equalsIgnoreCase(role) || "ROLE_MODERATOR".equalsIgnoreCase(role));
        if (!allowed) throw new BusinessException(iuh.fit.postservice.application.exception.PostServiceErrorCode.UNAUTHORIZED_ACTION);
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

    private void sendGroupPostRemovalNotification(Post post, UUID moderatorId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientId", post.getAuthorId().toString());
        notification.put("actorId", moderatorId.toString());
        notification.put("type", "SYSTEM");
        notification.put("title", "Bài viết trong nhóm đã bị gỡ");
        notification.put("content", "Lý do: " + post.getModerationReason());
        notification.put("targetId", post.getId().toString());
        notification.put("targetUrl", "/groups/" + post.getGroupId() + "?tab=appeals");
        notification.put("avatarUrl", null);
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
        for (String cacheName : List.of("post-feed-v2", "post-user-feed-v2", "post-detail-v2")) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }
}
