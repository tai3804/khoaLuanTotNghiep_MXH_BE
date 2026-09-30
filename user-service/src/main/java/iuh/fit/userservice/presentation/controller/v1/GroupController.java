package iuh.fit.userservice.presentation.controller.v1;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.service.GroupService;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import iuh.fit.userservice.presentation.dto.response.GroupMemberResponse;
import iuh.fit.userservice.presentation.dto.response.GroupFeedVisibilityResponse;
import iuh.fit.userservice.presentation.dto.request.UpdateGroupRequest;
import iuh.fit.userservice.domain.enums.GroupRole;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupController {

    GroupService groupService;
    JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(userIdStr);
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Táº¡o nhĂ³m thĂ nh cĂ´ng"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroupById(@PathVariable UUID id) {
        GroupResponse response = groupService.getGroupById(id, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Láº¥y thĂ´ng tin nhĂ³m thĂ nh cĂ´ng"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getGroups() { return ResponseEntity.ok(ApiResponse.success(groupService.getGroups(currentUserId()), "Groups retrieved")); }

    @PostMapping("/feed-visibility")
    public ResponseEntity<ApiResponse<List<GroupFeedVisibilityResponse>>> feedVisibility(@RequestBody List<UUID> groupIds) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getFeedVisibility(groupIds, currentUserId()), "Group feed visibility retrieved"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(@PathVariable UUID id, @RequestBody UpdateGroupRequest request) { return ResponseEntity.ok(ApiResponse.success(groupService.updateGroup(id, request, currentUserId()), "Group updated")); }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable UUID id) { groupService.deleteGroup(id, currentUserId()); return ResponseEntity.ok(ApiResponse.success(null, "Group deleted")); }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<GroupResponse>> join(@PathVariable UUID id) { return ResponseEntity.ok(ApiResponse.success(groupService.joinGroup(id, currentUserId()), "Join request processed")); }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> members(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean includePending) { return ResponseEntity.ok(ApiResponse.success(groupService.getMembers(id, currentUserId(), includePending), "Members retrieved")); }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<GroupResponse>> invite(@PathVariable UUID id, @RequestBody List<UUID> memberIds) { return ResponseEntity.ok(ApiResponse.success(groupService.addMembers(id, memberIds, currentUserId()), "Members added")); }

    @PostMapping("/{id}/members/{memberId}/review")
    public ResponseEntity<ApiResponse<GroupResponse>> review(@PathVariable UUID id, @PathVariable UUID memberId, @RequestParam boolean approved) { return ResponseEntity.ok(ApiResponse.success(groupService.reviewMember(id, memberId, approved, currentUserId()), "Member reviewed")); }

    @PutMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> role(@PathVariable UUID id, @PathVariable UUID memberId, @RequestParam GroupRole role) { groupService.changeRole(id, memberId, role, currentUserId()); return ResponseEntity.ok(ApiResponse.success(null, "Role updated")); }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id, @PathVariable UUID memberId, @RequestParam(defaultValue = "false") boolean ban) { groupService.removeMember(id, memberId, ban, currentUserId()); return ResponseEntity.ok(ApiResponse.success(null, "Member removed")); }

    private UUID currentUserId() { String id = jwtUtil.getCurrentUserId(); if (id == null) throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED); return UUID.fromString(id); }
}
