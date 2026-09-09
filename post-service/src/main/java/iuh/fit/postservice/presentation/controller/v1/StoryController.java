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
import iuh.fit.postservice.application.features.story.commands.create_story.CreateStoryCommand;
import iuh.fit.postservice.application.features.story.commands.create_story.CreateStoryHandler;
import iuh.fit.postservice.application.features.story.commands.delete_story.DeleteStoryCommand;
import iuh.fit.postservice.application.features.story.commands.delete_story.DeleteStoryHandler;
import iuh.fit.postservice.application.features.story.commands.view_story.ViewStoryCommand;
import iuh.fit.postservice.application.features.story.commands.view_story.ViewStoryHandler;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.GetActiveStoriesHandler;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.GetActiveStoriesQuery;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.StoryResult;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.UserStoriesResult;
import iuh.fit.postservice.application.features.story.queries.get_story_viewers.GetStoryViewersHandler;
import iuh.fit.postservice.application.features.story.queries.get_story_viewers.GetStoryViewersQuery;
import iuh.fit.postservice.application.features.story.queries.get_story_viewers.StoryViewerResult;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.dto.request.CreateStoryRequest;
import iuh.fit.postservice.presentation.dto.response.StoryResponse;
import iuh.fit.postservice.presentation.dto.response.StoryViewerResponse;
import iuh.fit.postservice.presentation.dto.response.UserStoriesResponse;
import iuh.fit.postservice.presentation.mapper.StoryPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.STORY_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "24h Story Management", description = "APIs for posting, viewing, and getting 24h stories and story viewers")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    CreateStoryHandler createStoryHandler;
    GetActiveStoriesHandler getActiveStoriesHandler;
    ViewStoryHandler viewStoryHandler;
    GetStoryViewersHandler getStoryViewersHandler;
    DeleteStoryHandler deleteStoryHandler;
    StoryPresentationMapper storyPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Post 24h Story", description = "Posts a new photo or video story that automatically expires after 24 hours")
    public ResponseEntity<ApiResponse<StoryResponse>> createStory(
            @Valid @RequestBody CreateStoryRequest request) {
        UUID currentUserId = getCurrentUserId();
        CreateStoryCommand command = storyPresentationMapper.toCommand(request, currentUserId);
        StoryResult result = createStoryHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(storyPresentationMapper.toResponse(result), "Story created successfully"));
    }

    @GetMapping
    @Operation(summary = "Get active stories", description = "Retrieves all active 24h stories of current user and followed users")
    public ResponseEntity<ApiResponse<List<UserStoriesResponse>>> getActiveStories() {
        UUID currentUserId = getCurrentUserId();
        GetActiveStoriesQuery query = GetActiveStoriesQuery.builder()
                .currentUserId(currentUserId)
                .build();
        List<UserStoriesResult> results = getActiveStoriesHandler.handle(query);
        List<UserStoriesResponse> responses = storyPresentationMapper.toUserStoriesResponseList(results);
        return ResponseEntity.ok(ApiResponse.success(responses, "Active stories retrieved successfully"));
    }

    @PostMapping("/{storyId}/view")
    @Operation(summary = "Record story view", description = "Marks a story as viewed by current user")
    public ResponseEntity<ApiResponse<Void>> viewStory(@PathVariable UUID storyId) {
        UUID currentUserId = getCurrentUserId();
        ViewStoryCommand command = ViewStoryCommand.builder()
                .storyId(storyId)
                .viewerId(currentUserId)
                .build();
        viewStoryHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Story view recorded"));
    }

    @GetMapping("/{storyId}/viewers")
    @Operation(summary = "Get story viewers list", description = "Retrieves paginated list and count of users who viewed author's story")
    public ResponseEntity<ApiResponse<PagedResponse<StoryViewerResponse>>> getStoryViewers(
            @PathVariable UUID storyId,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        UUID currentUserId = getCurrentUserId();
        GetStoryViewersQuery query = GetStoryViewersQuery.builder()
                .storyId(storyId)
                .currentUserId(currentUserId)
                .filter(filter)
                .build();
        PagedResponse<StoryViewerResult> result = getStoryViewersHandler.handle(query);
        PagedResponse<StoryViewerResponse> pagedResponse = storyPresentationMapper.toPagedViewerResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Story viewers retrieved successfully"));
    }

    @DeleteMapping("/{storyId}")
    @Operation(summary = "Delete story", description = "Soft deletes a 24h story")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable UUID storyId) {
        UUID currentUserId = getCurrentUserId();
        DeleteStoryCommand command = DeleteStoryCommand.builder()
                .storyId(storyId)
                .currentUserId(currentUserId)
                .build();
        deleteStoryHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Story deleted successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
