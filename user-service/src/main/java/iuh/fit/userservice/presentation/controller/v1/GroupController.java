package iuh.fit.userservice.presentation.controller.v1;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.service.GroupService;
import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupRole;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.request.JoinGroupRequest;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.dto.response.GroupStaffResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupController {

    GroupService groupService;
    JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(request, currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Tạo nhóm thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroupById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroupById(id, currentUserId()), "Lấy thông tin nhóm thành công"));
    }

    /** scope: all | discover | joined | pending | invited. Phân trang theo page (từ 1) và size (tối đa 50), tìm theo tên/mô tả bằng keyword. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getGroups(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<GroupResponse> result = groupService.getGroups(currentUserId(), scope, keyword, page, size);
        return ResponseEntity.ok(ApiResponse.paged(result.getContent(), result, "Lấy danh sách nhóm thành công"));
    }

    @PostMapping("/feed-visibility")
    public ResponseEntity<ApiResponse<List<GroupFeedVisibilityResponse>>> feedVisibility(@RequestBody List<UUID> groupIds) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getFeedVisibility(groupIds, currentUserId()), "Group feed visibility retrieved"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(@PathVariable UUID id, @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(groupService.updateGroup(id, request, currentUserId()), "Cập nhật nhóm thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable UUID id) {
        groupService.deleteGroup(id, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa nhóm"));
    }

    /** Tham gia nhóm (gọi lại nhiều lần cũng chỉ có tác dụng một lần). Nhóm riêng tư có câu hỏi thì gửi kèm answers. */
    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<GroupResponse>> join(@PathVariable UUID id, @RequestBody(required = false) JoinGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                groupService.joinGroup(id, request == null ? null : request.getAnswers(), currentUserId()), "Đã xử lý yêu cầu tham gia"));
    }

    /** Hủy yêu cầu tham gia đang chờ duyệt của chính mình. */
    @DeleteMapping("/{id}/join-request")
    public ResponseEntity<ApiResponse<GroupResponse>> cancelJoinRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.cancelJoinRequest(id, currentUserId()), "Đã hủy yêu cầu tham gia"));
    }

    /** Người được mời chấp nhận lời mời vào nhóm. */
    @PostMapping("/{id}/invitation/accept")
    public ResponseEntity<ApiResponse<GroupResponse>> acceptInvitation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.respondToInvitation(id, true, currentUserId()), "Đã chấp nhận lời mời"));
    }

    /** Người được mời từ chối lời mời vào nhóm. */
    @PostMapping("/{id}/invitation/decline")
    public ResponseEntity<ApiResponse<GroupResponse>> declineInvitation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.respondToInvitation(id, false, currentUserId()), "Đã từ chối lời mời"));
    }

    /** Mã mọi nhóm mình là thành viên (dùng cho bảng tin các nhóm ở post-service). */
    @GetMapping("/mine/ids")
    public ResponseEntity<ApiResponse<List<UUID>>> joinedGroupIds() {
        return ResponseEntity.ok(ApiResponse.success(groupService.getJoinedGroupIds(currentUserId()), "Lấy danh sách nhóm đã tham gia thành công"));
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable UUID id) {
        groupService.leaveGroup(id, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Bạn đã rời nhóm"));
    }

    /** includePending=true: quản trị/kiểm duyệt viên còn thấy cả người đang chờ duyệt, bị cấm và đang được mời (không phân trang). */
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> members(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean includePending) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getMembers(id, currentUserId(), includePending), "Lấy danh sách thành viên thành công"));
    }

    /** Thành viên phân trang. scope: all | members | staff | pending | banned | invited (ba loại cuối chỉ dành cho quản trị/kiểm duyệt viên). */
    @GetMapping("/{id}/members/page")
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> membersPage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GroupMemberResponse> result = groupService.getMembersPage(id, currentUserId(), scope, keyword, page, size);
        return ResponseEntity.ok(ApiResponse.paged(result.getContent(), result, "Lấy danh sách thành viên thành công"));
    }

    /** Quản trị viên và người kiểm duyệt của nhóm (ai cũng xem được, kể cả nhóm riêng tư). */
    @GetMapping("/{id}/staff")
    public ResponseEntity<ApiResponse<List<GroupStaffResponse>>> staff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getStaff(id), "Lấy danh sách quản trị viên thành công"));
    }

    /** Chọn mức nhận thông báo của nhóm: ALL (mọi bài viết) | HIGHLIGHTS (mặc định) | OFF. */
    @PutMapping("/{id}/members/me/notification-level")
    public ResponseEntity<ApiResponse<Void>> notificationLevel(@PathVariable UUID id, @RequestParam GroupNotificationLevel level) {
        groupService.setNotificationLevel(id, level, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã cập nhật cài đặt thông báo"));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<GroupResponse>> invite(@PathVariable UUID id, @RequestBody List<UUID> memberIds) {
        return ResponseEntity.ok(ApiResponse.success(groupService.addMembers(id, memberIds, currentUserId()), "Đã thêm thành viên"));
    }

    @PostMapping("/{id}/members/{memberId}/review")
    public ResponseEntity<ApiResponse<GroupResponse>> review(@PathVariable UUID id, @PathVariable UUID memberId, @RequestParam boolean approved) {
        return ResponseEntity.ok(ApiResponse.success(groupService.reviewMember(id, memberId, approved, currentUserId()), "Đã xử lý yêu cầu tham gia"));
    }

    @PutMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> role(@PathVariable UUID id, @PathVariable UUID memberId, @RequestParam GroupRole role) {
        groupService.changeRole(id, memberId, role, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã cập nhật vai trò"));
    }

    /** ban=true: cấm khỏi nhóm; deleteContent=true: gỡ luôn bài viết của người đó trong nhóm. */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id, @PathVariable UUID memberId,
                                                    @RequestParam(defaultValue = "false") boolean ban,
                                                    @RequestParam(defaultValue = "false") boolean deleteContent) {
        groupService.removeMember(id, memberId, ban, deleteContent, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ban ? "Đã cấm thành viên khỏi nhóm" : "Đã xóa thành viên khỏi nhóm"));
    }

    @PostMapping("/{id}/members/{memberId}/unban")
    public ResponseEntity<ApiResponse<Void>> unban(@PathVariable UUID id, @PathVariable UUID memberId) {
        groupService.unbanMember(id, memberId, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gỡ cấm thành viên"));
    }

    @PutMapping("/{id}/owner")
    public ResponseEntity<ApiResponse<Void>> transferOwner(@PathVariable UUID id, @RequestParam UUID newOwnerId) {
        groupService.transferOwnership(id, newOwnerId, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã chuyển quyền chủ nhóm"));
    }

    private UUID currentUserId() {
        String id = jwtUtil.getCurrentUserId();
        if (id == null) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        return UUID.fromString(id);
    }
}
