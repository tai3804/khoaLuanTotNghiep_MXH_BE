package iuh.fit.userservice.presentation.controller.internal.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.userservice.application.service.GroupService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API nội bộ về nhóm cho các service khác (cần header X-Internal-Key, không đi qua gateway). */
@RestController
@RequestMapping("/api/v1/internal/groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal Groups", description = "Internal APIs on community groups, used by other services")
public class InternalGroupController {

    GroupService groupService;

    @GetMapping("/{groupId}/notify-context")
    @Operation(summary = "Group name, staff ids and the members who asked for every new post, used by post-service to send notifications")
    public ResponseEntity<ApiResponse<GroupService.NotifyContext>> notifyContext(@PathVariable UUID groupId,
                                                                                @RequestParam(required = false) UUID actorId) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getNotifyContext(groupId, actorId), "Group notify context retrieved successfully"));
    }
}
