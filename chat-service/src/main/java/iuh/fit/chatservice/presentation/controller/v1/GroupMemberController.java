package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.member.commands.add_members.AddGroupMembersCommandHandler;
import iuh.fit.chatservice.application.features.member.commands.demote_admin.DemoteAdminCommandHandler;
import iuh.fit.chatservice.application.features.member.commands.leave_group.LeaveGroupCommandHandler;
import iuh.fit.chatservice.application.features.member.commands.promote_admin.PromoteAdminCommandHandler;
import iuh.fit.chatservice.application.features.member.commands.remove_member.RemoveGroupMemberCommandHandler;
import iuh.fit.chatservice.application.features.member.commands.update_nickname.UpdateNicknameCommandHandler;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.request.AddGroupMembersRequest;
import iuh.fit.chatservice.presentation.dto.request.UpdateNicknameRequest;
import iuh.fit.chatservice.presentation.mapper.ChatPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/conversations/{conversationId}/members")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Group Member Management", description = "APIs for managing group members, roles, admin promotions, and leaving groups")
@SecurityRequirement(name = "bearerAuth")
public class GroupMemberController {

    AddGroupMembersCommandHandler addGroupMembersCommandHandler;
    RemoveGroupMemberCommandHandler removeGroupMemberCommandHandler;
    PromoteAdminCommandHandler promoteAdminCommandHandler;
    DemoteAdminCommandHandler demoteAdminCommandHandler;
    LeaveGroupCommandHandler leaveGroupCommandHandler;
    UpdateNicknameCommandHandler updateNicknameCommandHandler;
    ChatPresentationMapper chatPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Add members to group", description = "Adds one or more users to the group (Max 250 total members)")
    public ResponseEntity<ApiResponse<Void>> addMembers(
            @PathVariable UUID conversationId,
            @Valid @RequestBody AddGroupMembersRequest request) {
        UUID currentUserId = getCurrentUserId();
        addGroupMembersCommandHandler.handle(chatPresentationMapper.toAddMembersCommand(request, conversationId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Members added successfully"));
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "Remove member from group", description = "Removes a member from the group (Admin only)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId) {
        UUID currentUserId = getCurrentUserId();
        removeGroupMemberCommandHandler.handle(chatPresentationMapper.toRemoveMemberCommand(conversationId, targetUserId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed successfully"));
    }

    @PostMapping("/{targetUserId}/promote-admin")
    @Operation(summary = "Promote member to admin", description = "Promotes a member to group Admin role (Admin only)")
    public ResponseEntity<ApiResponse<Void>> promoteAdmin(
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId) {
        UUID currentUserId = getCurrentUserId();
        promoteAdminCommandHandler.handle(chatPresentationMapper.toPromoteAdminCommand(conversationId, targetUserId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Member promoted to admin successfully"));
    }

    @PostMapping("/{targetUserId}/demote-admin")
    @Operation(summary = "Demote admin to member", description = "Demotes an admin to normal member role (Admin only)")
    public ResponseEntity<ApiResponse<Void>> demoteAdmin(
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId) {
        UUID currentUserId = getCurrentUserId();
        demoteAdminCommandHandler.handle(chatPresentationMapper.toDemoteAdminCommand(conversationId, targetUserId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Admin demoted to member successfully"));
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave group", description = "Removes current user from the group. Auto-promotes oldest member if sole admin leaves.")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(@PathVariable UUID conversationId) {
        UUID currentUserId = getCurrentUserId();
        leaveGroupCommandHandler.handle(chatPresentationMapper.toLeaveGroupCommand(conversationId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Left group successfully"));
    }

    @PutMapping("/{targetUserId}/nickname")
    @Operation(summary = "Update member nickname", description = "Sets or clears custom nickname for a conversation member")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateNicknameRequest request) {
        UUID currentUserId = getCurrentUserId();
        updateNicknameCommandHandler.handle(chatPresentationMapper.toUpdateNicknameCommand(request, conversationId, targetUserId, currentUserId));
        return ResponseEntity.ok(ApiResponse.success(null, "Nickname updated successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
