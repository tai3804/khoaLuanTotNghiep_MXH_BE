package iuh.fit.postservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.features.saved_post.commands.save_post.SavePostCommand;
import iuh.fit.postservice.application.features.saved_post.commands.save_post.SavePostHandler;
import iuh.fit.postservice.application.features.saved_post.commands.unsave_post.UnsavePostCommand;
import iuh.fit.postservice.application.features.saved_post.commands.unsave_post.UnsavePostHandler;
import iuh.fit.postservice.application.features.saved_post.queries.get_saved_posts.GetSavedPostsHandler;
import iuh.fit.postservice.application.features.saved_post.queries.get_saved_posts.GetSavedPostsQuery;
import iuh.fit.postservice.domain.entities.SavedPost;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.dto.request.SavePostRequest;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import iuh.fit.postservice.presentation.dto.response.SavedPostResponse;
import iuh.fit.postservice.presentation.mapper.PostPresentationMapper;
import iuh.fit.postservice.presentation.mapper.SavedPostPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.POST_API + "/saved")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Saved Posts / Bookmarks", description = "APIs for saving posts, unsaving posts, and retrieving saved post collections")
@SecurityRequirement(name = "bearerAuth")
public class SavedPostController {

    SavePostHandler savePostHandler;
    UnsavePostHandler unsavePostHandler;
    GetSavedPostsHandler getSavedPostsHandler;
    SavedPostPresentationMapper savedPostPresentationMapper;
    PostPresentationMapper postPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Save post", description = "Saves a post to user's bookmark collection")
    public ResponseEntity<ApiResponse<SavedPostResponse>> savePost(
            @Valid @RequestBody SavePostRequest request) {
        UUID currentUserId = getCurrentUserId();
        SavePostCommand command = savedPostPresentationMapper.toCommand(request, currentUserId);
        SavedPost result = savePostHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(savedPostPresentationMapper.toResponse(result), "Post saved successfully"));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Unsave post", description = "Removes a post from user's bookmark collection")
    public ResponseEntity<ApiResponse<Void>> unsavePost(@PathVariable UUID postId) {
        UUID currentUserId = getCurrentUserId();
        UnsavePostCommand command = UnsavePostCommand.builder()
                .userId(currentUserId)
                .postId(postId)
                .build();
        unsavePostHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Post unsaved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get saved posts", description = "Retrieves paginated list of posts saved by the authenticated user")
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getSavedPosts(
            @RequestParam(required = false) String collectionName,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        UUID currentUserId = getCurrentUserId();
        GetSavedPostsQuery query = GetSavedPostsQuery.builder()
                .userId(currentUserId)
                .collectionName(collectionName)
                .filter(filter)
                .build();

        PagedResponse<GetPostDetailResult> result = getSavedPostsHandler.handle(query);
        PagedResponse<PostResponse> pagedResponse = postPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Saved posts retrieved successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
