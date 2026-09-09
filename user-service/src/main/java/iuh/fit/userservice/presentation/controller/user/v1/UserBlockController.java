package iuh.fit.userservice.presentation.controller.user.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.features.user_block.commands.block_user.BlockUserCommand;
import iuh.fit.userservice.application.features.user_block.commands.block_user.BlockUserCommandHandler;
import iuh.fit.userservice.application.features.user_block.commands.unblock_user.UnblockUserCommand;
import iuh.fit.userservice.application.features.user_block.commands.unblock_user.UnblockUserCommandHandler;
import iuh.fit.userservice.application.features.user_block.queries.check_user_block.CheckUserBlockQuery;
import iuh.fit.userservice.application.features.user_block.queries.check_user_block.CheckUserBlockQueryHandler;
import iuh.fit.userservice.application.features.user_block.queries.check_user_block.CheckUserBlockQueryResult;
import iuh.fit.userservice.application.features.user_block.queries.get_blocked_users.GetBlockedUsersQuery;
import iuh.fit.userservice.application.features.user_block.queries.get_blocked_users.GetBlockedUsersQueryHandler;
import iuh.fit.userservice.application.features.user_block.queries.get_blocked_users.UserBlockResult;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.dto.request.BlockUserRequest;
import iuh.fit.userservice.presentation.dto.response.CheckBlockResponse;
import iuh.fit.userservice.presentation.dto.response.UserBlockResponse;
import iuh.fit.userservice.presentation.mapper.UserBlockPresentationMapper;
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
@RequestMapping(ApiConstants.USER_BLOCK_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Block Management", description = "APIs for blocking, unblocking, and checking block status between users")
@SecurityRequirement(name = "bearerAuth")
public class UserBlockController {

    BlockUserCommandHandler blockUserCommandHandler;
    UnblockUserCommandHandler unblockUserCommandHandler;
    GetBlockedUsersQueryHandler getBlockedUsersQueryHandler;
    CheckUserBlockQueryHandler checkUserBlockQueryHandler;
    UserBlockPresentationMapper userBlockPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/{targetId}")
    @Operation(summary = "Block user", description = "Blocks another user, preventing interaction and connections")
    public ResponseEntity<ApiResponse<Void>> blockUser(
            @PathVariable UUID targetId,
            @RequestBody(required = false) BlockUserRequest request) {
        UUID currentUserId = getCurrentUserId();
        BlockUserCommand command = BlockUserCommand.builder()
                .blockerId(currentUserId)
                .targetId(targetId)
                .reason(request != null ? request.getReason() : null)
                .build();
        blockUserCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "User blocked successfully"));
    }

    @DeleteMapping("/{targetId}")
    @Operation(summary = "Unblock user", description = "Unblocks a previously blocked user")
    public ResponseEntity<ApiResponse<Void>> unblockUser(@PathVariable UUID targetId) {
        UUID currentUserId = getCurrentUserId();

        UnblockUserCommand command = UnblockUserCommand.builder()
                .blockerId(currentUserId)
                .targetId(targetId)
                .build();

        unblockUserCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "User unblocked successfully"));
    }

    @GetMapping
    @Operation(summary = "Get blocked users list", description = "Retrieves paginated list of users blocked by the authenticated user")
    public ResponseEntity<ApiResponse<List<UserBlockResponse>>> getBlockedUsers(
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        UUID currentUserId = getCurrentUserId();

        GetBlockedUsersQuery query = GetBlockedUsersQuery.builder()
                .blockerId(currentUserId)
                .filter(filter)
                .build();

        PagedResponse<UserBlockResult> result = getBlockedUsersQueryHandler.handle(query);
        PagedResponse<UserBlockResponse> pagedResponse = userBlockPresentationMapper.toPagedResponse(result);

        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Blocked users retrieved successfully"));
    }

    @GetMapping("/check/{targetId}")
    @Operation(summary = "Check block status with target user", description = "Checks whether current user blocks or is blocked by the target user")
    public ResponseEntity<ApiResponse<CheckBlockResponse>> checkUserBlock(@PathVariable UUID targetId) {
        UUID currentUserId = getCurrentUserId();

        CheckUserBlockQuery query = CheckUserBlockQuery.builder()
                .userId(currentUserId)
                .targetId(targetId)
                .build();

        CheckUserBlockQueryResult result = checkUserBlockQueryHandler.handle(query);
        CheckBlockResponse response = userBlockPresentationMapper.toResponse(result);

        return ResponseEntity.ok(ApiResponse.success(response, "Block status checked successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(UserServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
