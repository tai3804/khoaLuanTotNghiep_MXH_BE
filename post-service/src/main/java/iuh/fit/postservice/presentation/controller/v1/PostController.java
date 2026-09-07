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
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.constants.MessageConstants;
import iuh.fit.postservice.presentation.dto.request.CreatePostRequest;
import iuh.fit.postservice.presentation.dto.request.SharePostRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostRequest;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import iuh.fit.postservice.presentation.mapper.PostPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    PostPresentationMapper postPresentationMapper;
    JwtUtil jwtUtil;

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
    @Operation(summary = "Get all posts", description = "Retrieves paginated list of posts using BaseFilter (keyword, filters map, page, size, sortBy, sortDirection)")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getAllPosts(@ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        GetAllPostsQuery query = GetAllPostsQuery.builder().filter(filter).build();
        PagedResponse<GetPostDetailResult> result = getAllPostsQueryHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.POSTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post details", description = "Retrieves detail information of a specific post by ID")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable UUID postId) {
        GetPostDetailQuery query = GetPostDetailQuery.builder().postId(postId).build();
        GetPostDetailResult result = getPostDetailQueryHandler.handle(query);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_RETRIEVED_SUCCESSFULLY));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update post", description = "Updates content or privacy of an existing post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request) {
        UUID currentUserId = getCurrentUserId();
        UpdatePostCommand command = postPresentationMapper.toUpdateCommand(request, postId, currentUserId);
        UpdatePostResult result = updatePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_UPDATED_SUCCESSFULLY));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete post", description = "Deletes a post and cleans up associated media files")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable UUID postId) {
        UUID currentUserId = getCurrentUserId();
        DeletePostCommand command = DeletePostCommand.builder().postId(postId).userId(currentUserId).build();
        deletePostCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.POST_DELETED_SUCCESSFULLY));
    }

    @PostMapping("/{postId}/share")
    @Operation(summary = "Share post", description = "Shares an existing post as a new post")
    public ResponseEntity<ApiResponse<PostResponse>> sharePost(
            @PathVariable UUID postId,
            @Valid @RequestBody SharePostRequest request) {
        UUID currentUserId = getCurrentUserId();
        SharePostCommand command = postPresentationMapper.toShareCommand(request, postId, currentUserId);
        SharePostResult result = sharePostCommandHandler.handle(command);
        PostResponse response = postPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.POST_SHARED_SUCCESSFULLY));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user posts", description = "Retrieves paginated list of posts created by a specific user")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getUserPosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        GetUserPostsQuery query = GetUserPostsQuery.builder().userId(userId).page(page).size(size).build();
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
}
