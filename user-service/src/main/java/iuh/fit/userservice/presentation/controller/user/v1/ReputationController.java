package iuh.fit.userservice.presentation.controller.user.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.service.ReputationService;
import iuh.fit.userservice.domain.entities.ReputationEvent;
import iuh.fit.userservice.presentation.constants.ApiConstants;
import iuh.fit.userservice.presentation.dto.response.ReputationHistoryResponse;
import iuh.fit.userservice.presentation.dto.response.ReputationResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.USER_API + "/reputation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Reputation", description = "APIs for the reputation score of users (starts at 100, decreases on violations)")
@SecurityRequirement(name = "bearerAuth")
public class ReputationController {

    ReputationService reputationService;
    JwtUtil jwtUtil;

    @GetMapping("/me")
    @Operation(summary = "Get my reputation score and level")
    public ResponseEntity<ApiResponse<ReputationResponse>> getMyReputation() {
        return ResponseEntity.ok(ApiResponse.success(reputationService.getReputation(getCurrentUserId()), "Reputation retrieved successfully"));
    }

    @GetMapping("/me/history")
    @Operation(summary = "Get my reputation history")
    public ResponseEntity<ApiResponse<List<ReputationHistoryResponse>>> getMyHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(history(getCurrentUserId(), page, size));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get the reputation of a user (the user themselves, moderators or admins only)")
    public ResponseEntity<ApiResponse<ReputationResponse>> getReputation(@PathVariable UUID userId) {
        requireSelfOrStaff(userId);
        return ResponseEntity.ok(ApiResponse.success(reputationService.getReputation(userId), "Reputation retrieved successfully"));
    }

    @GetMapping("/{userId}/history")
    @Operation(summary = "Get the reputation history of a user (the user themselves, moderators or admins only)")
    public ResponseEntity<ApiResponse<List<ReputationHistoryResponse>>> getHistory(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSelfOrStaff(userId);
        return ResponseEntity.ok(history(userId, page, size));
    }

    private ApiResponse<List<ReputationHistoryResponse>> history(UUID userId, int page, int size) {
        Page<ReputationEvent> events = reputationService.getHistory(userId, PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, size), 100)));
        List<ReputationHistoryResponse> items = events.getContent().stream()
                .map(e -> ReputationHistoryResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .action(e.getAction())
                        .sourceType(e.getSourceType())
                        .sourceId(e.getSourceId())
                        .points(e.getPoints())
                        .scoreBefore(e.getScoreBefore())
                        .scoreAfter(e.getScoreAfter())
                        .reason(e.getReason())
                        .reverted(e.isReverted())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
        return ApiResponse.paged(items, events, "Reputation history retrieved successfully");
    }

    private void requireSelfOrStaff(UUID userId) {
        if (userId.equals(getCurrentUserId())) {
            return;
        }
        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean staff = roles != null && roles.stream().anyMatch(r ->
                "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r)
                        || "MODERATOR".equalsIgnoreCase(r) || "ROLE_MODERATOR".equalsIgnoreCase(r));
        if (!staff) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private UUID getCurrentUserId() {
        String userId = jwtUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return UUID.fromString(userId);
    }
}
