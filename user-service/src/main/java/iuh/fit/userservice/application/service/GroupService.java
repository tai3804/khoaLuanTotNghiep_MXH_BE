package iuh.fit.userservice.application.service;

import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.domain.enums.GroupRole;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, UUID creatorId);
    GroupResponse getGroupById(UUID groupId, UUID viewerId);
    List<GroupResponse> getGroups(UUID viewerId);
    GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId);
    void deleteGroup(UUID groupId, UUID currentUserId);
    GroupResponse joinGroup(UUID groupId, UUID currentUserId);
    void leaveGroup(UUID groupId, UUID currentUserId);
    GroupResponse reviewMember(UUID groupId, UUID memberId, boolean approved, UUID currentUserId);
    GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId);
    List<GroupMemberResponse> getMembers(UUID groupId, UUID currentUserId, boolean includePending);
    void changeRole(UUID groupId, UUID memberId, GroupRole role, UUID currentUserId);
    void removeMember(UUID groupId, UUID memberId, boolean ban, UUID currentUserId);
    List<GroupFeedVisibilityResponse> getFeedVisibility(List<UUID> groupIds, UUID viewerId);
}

