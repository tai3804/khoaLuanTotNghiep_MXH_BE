package iuh.fit.feedservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.feedservice.application.exception.FeedServiceErrorCode;
import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedQuery;
import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedQueryHandler;
import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedResult;
import iuh.fit.feedservice.presentation.constants.ApiConstants;
import iuh.fit.feedservice.presentation.constants.MessageConstants;
import iuh.fit.feedservice.presentation.dto.response.FeedItemResponse;
import iuh.fit.feedservice.presentation.mapper.FeedPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.FEED_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Feed Management", description = "APIs for retrieving personalized user timeline newsfeed")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {

    GetUserFeedQueryHandler getUserFeedQueryHandler;
    FeedPresentationMapper feedPresentationMapper;
    JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get current user newsfeed", description = "Retrieves paginated personalized newsfeed of posts from friends and followed users")
    public ResponseEntity<ApiResponse<PagedResponse<FeedItemResponse>>> getMyFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID currentUserId = getCurrentUserId();
        GetUserFeedQuery query = feedPresentationMapper.toQuery(currentUserId, page, size);
        PagedResponse<GetUserFeedResult> result = getUserFeedQueryHandler.handle(query);
        PagedResponse<FeedItemResponse> response = feedPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.FEED_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get specific user timeline feed", description = "Retrieves paginated newsfeed for a specific user ID")
    public ResponseEntity<ApiResponse<PagedResponse<FeedItemResponse>>> getUserFeed(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        GetUserFeedQuery query = feedPresentationMapper.toQuery(userId, page, size);
        PagedResponse<GetUserFeedResult> result = getUserFeedQueryHandler.handle(query);
        PagedResponse<FeedItemResponse> response = feedPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.FEED_RETRIEVED_SUCCESSFULLY));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(FeedServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
