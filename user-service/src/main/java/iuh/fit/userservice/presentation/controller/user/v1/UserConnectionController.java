package iuh.fit.userservice.presentation.controller.user.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.features.user_connection.commands.accept_friend_request.AcceptFriendRequestCommandHandler;
import iuh.fit.userservice.application.features.user_connection.commands.follow_user.FollowUserCommandHandler;
import iuh.fit.userservice.application.features.user_connection.commands.reject_friend_request.RejectFriendRequestCommandHandler;
import iuh.fit.userservice.application.features.user_connection.commands.send_friend_request.SendFriendRequestCommandHandler;
import iuh.fit.userservice.application.features.user_connection.commands.unfollow_user.UnfollowUserCommandHandler;
import iuh.fit.userservice.application.features.user_connection.commands.unfriend_user.UnfriendUserCommandHandler;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.GetConnectionsQueryHandler;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.UserConnectionResult;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.constants.MessageConstants;
import iuh.fit.userservice.presentation.dto.response.UserConnectionResponse;
import iuh.fit.userservice.presentation.mapper.UserConnectionPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.USER_API + "/connections")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Connections", description = "APIs for follow, friend requests, and user connections management")
public class UserConnectionController {

    SendFriendRequestCommandHandler sendFriendRequestCommandHandler;
    AcceptFriendRequestCommandHandler acceptFriendRequestCommandHandler;
    RejectFriendRequestCommandHandler rejectFriendRequestCommandHandler;
    FollowUserCommandHandler followUserCommandHandler;
    UnfollowUserCommandHandler unfollowUserCommandHandler;
    UnfriendUserCommandHandler unfriendUserCommandHandler;
    GetConnectionsQueryHandler getConnectionsQueryHandler;
    UserConnectionPresentationMapper userConnectionPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/friend-requests/{targetId}")
    @Operation(summary = "Send friend request", description = "Sends a friend request to a user and automatically follows them", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> sendFriendRequest(@PathVariable UUID targetId) {
        UUID userId = getCurrentUserId();
        sendFriendRequestCommandHandler.handle(userConnectionPresentationMapper.toSendFriendRequestCommand(userId, targetId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.FRIEND_REQUEST_SENT));
    }

    @PostMapping("/friend-requests/{requesterId}/accept")
    @Operation(summary = "Accept friend request", description = "Accepts a pending friend request from a user and mutually follows them", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> acceptFriendRequest(@PathVariable UUID requesterId) {
        UUID userId = getCurrentUserId();
        acceptFriendRequestCommandHandler.handle(userConnectionPresentationMapper.toAcceptFriendRequestCommand(userId, requesterId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.FRIEND_REQUEST_ACCEPTED));
    }

    @PostMapping("/friend-requests/{requesterId}/reject")
    @Operation(summary = "Reject friend request", description = "Rejects a pending friend request from a user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> rejectFriendRequest(@PathVariable UUID requesterId) {
        UUID userId = getCurrentUserId();
        rejectFriendRequestCommandHandler.handle(userConnectionPresentationMapper.toRejectFriendRequestCommand(userId, requesterId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.FRIEND_REQUEST_REJECTED));
    }

    @PostMapping("/follow/{targetId}")
    @Operation(summary = "Follow user", description = "Follows a user. If mutual follow occurs, automatically becomes friends", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> followUser(@PathVariable UUID targetId) {
        UUID userId = getCurrentUserId();
        followUserCommandHandler.handle(userConnectionPresentationMapper.toFollowUserCommand(userId, targetId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.USER_FOLLOWED));
    }

    @PostMapping("/unfollow/{targetId}")
    @Operation(summary = "Unfollow user", description = "Unfollows a user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> unfollowUser(@PathVariable UUID targetId) {
        UUID userId = getCurrentUserId();
        unfollowUserCommandHandler.handle(userConnectionPresentationMapper.toUnfollowUserCommand(userId, targetId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.USER_UNFOLLOWED));
    }

    @DeleteMapping("/friends/{friendId}")
    @Operation(summary = "Unfriend user", description = "Removes a friend connection", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> unfriendUser(@PathVariable UUID friendId) {
        UUID userId = getCurrentUserId();
        unfriendUserCommandHandler.handle(userConnectionPresentationMapper.toUnfriendUserCommand(userId, friendId));
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.UNFRIENDED));
    }

    @GetMapping("/friends")
    @Operation(summary = "Get friends list", description = "Retrieves the list of accepted friends", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PagedResponse<UserConnectionResponse>>> getFriends(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        return getPagedConnectionsResponse(userId, "FRIENDS", page, size);
    }

    @GetMapping("/followers")
    @Operation(summary = "Get followers list", description = "Retrieves the list of users following the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PagedResponse<UserConnectionResponse>>> getFollowers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        return getPagedConnectionsResponse(userId, "FOLLOWERS", page, size);
    }

    @GetMapping("/following")
    @Operation(summary = "Get following list", description = "Retrieves the list of users the current user is following", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PagedResponse<UserConnectionResponse>>> getFollowing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        return getPagedConnectionsResponse(userId, "FOLLOWING", page, size);
    }

    @GetMapping("/friend-requests/pending")
    @Operation(summary = "Get pending friend requests", description = "Retrieves the list of incoming pending friend requests", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PagedResponse<UserConnectionResponse>>> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        return getPagedConnectionsResponse(userId, "PENDING_REQUESTS", page, size);
    }

    private ResponseEntity<ApiResponse<PagedResponse<UserConnectionResponse>>> getPagedConnectionsResponse(
            UUID userId, String mode, int page, int size) {
        PagedResponse<UserConnectionResult> result = getConnectionsQueryHandler.handle(
                userConnectionPresentationMapper.toGetConnectionsQuery(userId, mode, page, size)
        );

        PagedResponse<UserConnectionResponse> response = userConnectionPresentationMapper.toPagedResponse(result);

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.CONNECTIONS_RETRIEVED));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
