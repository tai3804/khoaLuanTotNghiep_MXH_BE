package iuh.fit.userservice.application.service;

import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, UUID creatorId);

    GroupResponse getGroupById(UUID groupId, UUID viewerId);

    /**
     * Danh sách nhóm có phân trang và tìm kiếm.
     * scope: all (mặc định) | discover (chưa tham gia) | joined (đã tham gia) | pending (đang chờ duyệt).
     * page bắt đầu từ 1.
     */
    Page<GroupResponse> getGroups(UUID viewerId, String scope, String keyword, int page, int size);

    GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId);

    void deleteGroup(UUID groupId, UUID currentUserId);

    GroupResponse joinGroup(UUID groupId, UUID currentUserId);

    void leaveGroup(UUID groupId, UUID currentUserId);

    GroupResponse reviewMember(UUID groupId, UUID memberId, boolean approved, UUID currentUserId);

    GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId);

    /** Quản trị/kiểm duyệt viên với includePending=true còn thấy cả người đang chờ duyệt và người bị cấm. */
    List<GroupMemberResponse> getMembers(UUID groupId, UUID currentUserId, boolean includePending);

    void changeRole(UUID groupId, UUID memberId, GroupRole role, UUID currentUserId);

    void removeMember(UUID groupId, UUID memberId, boolean ban, UUID currentUserId);

    /** Gỡ cấm: xóa bản ghi bị cấm để người đó có thể xin tham gia lại. */
    void unbanMember(UUID groupId, UUID memberId, UUID currentUserId);

    /** Chủ nhóm chuyển quyền chủ nhóm cho một thành viên đã được duyệt (chủ cũ vẫn là quản trị viên). */
    void transferOwnership(UUID groupId, UUID newOwnerId, UUID currentUserId);

    List<GroupFeedVisibilityResponse> getFeedVisibility(List<UUID> groupIds, UUID viewerId);
}
