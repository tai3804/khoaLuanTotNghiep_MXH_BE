package iuh.fit.userservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_connection.commands.accept_friend_request.AcceptFriendRequestCommand;
import iuh.fit.userservice.application.features.user_connection.commands.follow_user.FollowUserCommand;
import iuh.fit.userservice.application.features.user_connection.commands.reject_friend_request.RejectFriendRequestCommand;
import iuh.fit.userservice.application.features.user_connection.commands.send_friend_request.SendFriendRequestCommand;
import iuh.fit.userservice.application.features.user_connection.commands.unfollow_user.UnfollowUserCommand;
import iuh.fit.userservice.application.features.user_connection.commands.unfriend_user.UnfriendUserCommand;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.GetConnectionsQuery;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.UserConnectionResult;
import iuh.fit.userservice.presentation.dto.response.UserConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserConnectionPresentationMapper {

    UserConnectionResponse toResponse(UserConnectionResult result);

    PagedResponse<UserConnectionResponse> toPagedResponse(PagedResponse<UserConnectionResult> pagedResult);

    @Mapping(target = "requesterId", source = "userId")
    @Mapping(target = "targetId", source = "targetId")
    SendFriendRequestCommand toSendFriendRequestCommand(UUID userId, UUID targetId);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "requesterId", source = "requesterId")
    AcceptFriendRequestCommand toAcceptFriendRequestCommand(UUID userId, UUID requesterId);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "requesterId", source = "requesterId")
    RejectFriendRequestCommand toRejectFriendRequestCommand(UUID userId, UUID requesterId);

    @Mapping(target = "followerId", source = "userId")
    @Mapping(target = "targetId", source = "targetId")
    FollowUserCommand toFollowUserCommand(UUID userId, UUID targetId);

    @Mapping(target = "followerId", source = "userId")
    @Mapping(target = "targetId", source = "targetId")
    UnfollowUserCommand toUnfollowUserCommand(UUID userId, UUID targetId);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "friendId", source = "friendId")
    UnfriendUserCommand toUnfriendUserCommand(UUID userId, UUID friendId);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "mode", source = "mode")
    @Mapping(target = "page", source = "page")
    @Mapping(target = "size", source = "size")
    GetConnectionsQuery toGetConnectionsQuery(UUID userId, String mode, int page, int size);
}
