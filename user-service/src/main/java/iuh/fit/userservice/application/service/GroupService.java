package iuh.fit.userservice.application.service;

import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.dto.response.GroupStaffResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, UUID creatorId);

    GroupResponse getGroupById(UUID groupId, UUID viewerId);

    /**
     * Danh sách nhóm có phân trang và tìm kiếm.
     * scope: all (mặc định) | discover (chưa tham gia) | joined (đã tham gia) | pending (đang chờ duyệt) | invited (đang được mời).
     * page bắt đầu từ 1.
     */
    Page<GroupResponse> getGroups(UUID viewerId, String scope, String keyword, int page, int size);

    GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId);

    void deleteGroup(UUID groupId, UUID currentUserId);

    /** Tham gia nhóm (idempotent). Nhóm riêng tư có câu hỏi thì cần kèm câu trả lời. */
    GroupResponse joinGroup(UUID groupId, List<String> answers, UUID currentUserId);

    /** Hủy yêu cầu tham gia đang chờ duyệt của chính mình. */
    GroupResponse cancelJoinRequest(UUID groupId, UUID currentUserId);

    void leaveGroup(UUID groupId, UUID currentUserId);

    GroupResponse reviewMember(UUID groupId, UUID memberId, boolean approved, UUID currentUserId);

    /**
     * Quản trị/kiểm duyệt viên thêm thẳng bạn bè vào nhóm; thành viên thường chỉ gửi được lời mời
     * (người được mời phải chấp nhận). Chỉ mời được bạn bè của mình.
     */
    GroupResponse addMembers(UUID groupId, List<UUID> memberIds, UUID currentUserId);

    /** Người được mời chấp nhận hoặc từ chối lời mời vào nhóm. */
    GroupResponse respondToInvitation(UUID groupId, boolean accept, UUID currentUserId);

    /** Quản trị/kiểm duyệt viên với includePending=true còn thấy cả người đang chờ duyệt, bị cấm và đang được mời. */
    List<GroupMemberResponse> getMembers(UUID groupId, UUID currentUserId, boolean includePending);

    /**
     * Danh sách thành viên phân trang (page từ 1).
     * scope: all (mặc định) | members (chỉ thành viên thường) | staff | pending | banned | invited; ba scope cuối chỉ dành cho quản trị/kiểm duyệt viên.
     */
    Page<GroupMemberResponse> getMembersPage(UUID groupId, UUID currentUserId, String scope, String keyword, int page, int size);

    /** Quản trị viên và người kiểm duyệt: ai đăng nhập cũng xem được. */
    List<GroupStaffResponse> getStaff(UUID groupId);

    void changeRole(UUID groupId, UUID memberId, GroupRole role, UUID currentUserId);

    /** deleteContent=true: bài viết của người đó trong nhóm cũng bị gỡ. */
    void removeMember(UUID groupId, UUID memberId, boolean ban, boolean deleteContent, UUID currentUserId);

    /** Gỡ cấm: xóa bản ghi bị cấm để người đó có thể xin tham gia lại. */
    void unbanMember(UUID groupId, UUID memberId, UUID currentUserId);

    /** Chủ nhóm chuyển quyền chủ nhóm cho một thành viên đã được duyệt (chủ cũ vẫn là quản trị viên). */
    void transferOwnership(UUID groupId, UUID newOwnerId, UUID currentUserId);

    void setNotificationLevel(UUID groupId, GroupNotificationLevel level, UUID currentUserId);

    /** Mã mọi nhóm mà người dùng là thành viên đã được duyệt (dùng cho bảng tin các nhóm). */
    List<UUID> getJoinedGroupIds(UUID userId);

    List<GroupFeedVisibilityResponse> getFeedVisibility(List<UUID> groupIds, UUID viewerId);

    /**
     * Thông tin để post-service gửi thông báo: tên nhóm, quản trị/kiểm duyệt viên, người chọn nhận mọi bài viết mới
     * và tên/ảnh đại diện của người thực hiện (actorId, có thể null).
     */
    NotifyContext getNotifyContext(UUID groupId, UUID actorId);

    record NotifyContext(UUID groupId, String name, List<UUID> staffIds, List<UUID> subscriberIds, String actorName, String actorAvatar) { }
}
