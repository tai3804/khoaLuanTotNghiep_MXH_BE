package iuh.fit.userservice.infrastructure.persistence;

import iuh.fit.userservice.application.service.suggestion.FriendSuggestionCache;
import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.entities.UserBlock;
import iuh.fit.userservice.domain.entities.UserConnection;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Xoá cache gợi ý kết bạn của các bên liên quan mỗi khi quan hệ bạn bè/theo dõi, lệnh chặn hoặc thành viên nhóm thay đổi,
 * để gợi ý luôn phản ánh đúng quan hệ hiện tại mà không cần sửa từng command handler.
 * Việc xoá chỉ chạy sau khi giao dịch DB đã commit (tránh một lần đọc song song ghi lại dữ liệu cũ vào cache).
 */
@Component
@RequiredArgsConstructor
public class SuggestionCacheEvictionListener {

    private final FriendSuggestionCache friendSuggestionCache;

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        UUID[] affected = affectedUsers(entity);
        if (affected == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    friendSuggestionCache.evict(affected);
                }
            });
        } else {
            friendSuggestionCache.evict(affected);
        }
    }

    private static UUID[] affectedUsers(Object entity) {
        if (entity instanceof UserConnection c) return new UUID[]{c.getRequesterId(), c.getTargetId()};
        if (entity instanceof UserBlock b) return new UUID[]{b.getBlockerId(), b.getBlockedId()};
        if (entity instanceof GroupMember m) return new UUID[]{m.getUserId()};
        return null;
    }
}
