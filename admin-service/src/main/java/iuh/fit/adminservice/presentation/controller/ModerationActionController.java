package iuh.fit.adminservice.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.adminservice.application.dto.request.ModeratePostRequest;
import iuh.fit.adminservice.application.dto.response.ModerationLogResponse;
import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.application.features.moderation.commands.moderate_post.ModeratePostCommand;
import iuh.fit.adminservice.application.features.moderation.commands.moderate_post.ModeratePostCommandHandler;
import iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsQuery;
import iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsQueryHandler;
import iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsResult;
import iuh.fit.adminservice.presentation.constants.ApiConstants;
import iuh.fit.adminservice.presentation.mapper.ModerationPresentationMapper;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.MODERATION_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Content Moderation Management", description = "APIs for direct content moderation and viewing moderation audit logs")
@SecurityRequirement(name = "bearerAuth")
public class ModerationActionController {

    ModeratePostCommandHandler moderatePostCommandHandler;
    GetModerationLogsQueryHandler getModerationLogsQueryHandler;
    ModerationPresentationMapper moderationPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/posts/{postId}/delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Directly delete/moderate a post (Moderator only)", description = "Allows moderators to directly delete a post and publish moderation event to Kafka")
    public ResponseEntity<ApiResponse<Void>> moderatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody ModeratePostRequest request) {
        UUID moderatorId = getCurrentUserId();
        ModeratePostCommand command = moderationPresentationMapper.toModeratePostCommand(request, postId, moderatorId);
        moderatePostCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Post moderated and deleted successfully"));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get moderation audit logs (Moderator/Admin only)", description = "Retrieves paginated audit log history of moderation actions")
    public ResponseEntity<ApiResponse<List<ModerationLogResponse>>> getModerationLogs(@ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        GetModerationLogsQuery query = GetModerationLogsQuery.builder().filter(filter).build();
        PagedResponse<GetModerationLogsResult> result = getModerationLogsQueryHandler.handle(query);
        PagedResponse<ModerationLogResponse> pagedResponse = moderationPresentationMapper.toPagedLogResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Moderation logs retrieved successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(AdminServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
