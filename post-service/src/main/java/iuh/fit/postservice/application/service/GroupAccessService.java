package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Kiểm tra quyền của người đang đăng nhập trong một nhóm (hỏi user-service, nơi quản lý thành viên nhóm). */
@Service
@RequiredArgsConstructor
public class GroupAccessService {

    private final UserConnectionClient userConnectionClient;

    /** Quan hệ của mình với nhóm; null nếu nhóm không tồn tại. Ném 503 khi không hỏi được user-service. */
    public UserConnectionClient.GroupFeedVisibility visibility(UUID groupId) {
        try {
            ApiResponse<List<UserConnectionClient.GroupFeedVisibility>> response = userConnectionClient.getGroupFeedVisibility(List.of(groupId));
            return response == null || response.getData() == null ? null : response.getData().stream()
                    .filter(item -> groupId.equals(item.groupId())).findFirst().orElse(null);
        } catch (Exception e) {
            throw new BusinessException(PostServiceErrorCode.GROUP_SERVICE_UNAVAILABLE);
        }
    }

    /** Phải là thành viên đã được duyệt của nhóm (ném 403 nếu không). */
    public UserConnectionClient.GroupFeedVisibility requireMember(UUID groupId) {
        UserConnectionClient.GroupFeedVisibility visibility = visibility(groupId);
        if (visibility == null || !visibility.member()) {
            throw new BusinessException(PostServiceErrorCode.GROUP_MEMBERSHIP_REQUIRED);
        }
        return visibility;
    }

    /** Phải là quản trị viên hoặc người kiểm duyệt của nhóm (ném 403 nếu không). */
    public UserConnectionClient.GroupFeedVisibility requireModerator(UUID groupId) {
        UserConnectionClient.GroupFeedVisibility visibility = visibility(groupId);
        if (visibility == null || !visibility.member()
                || !("ADMIN".equals(visibility.role()) || "MODERATOR".equals(visibility.role()))) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }
        return visibility;
    }
}
