package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostVisibilityService {
    private final UserConnectionClient userConnectionClient;

    public boolean canView(Post post, UUID viewerId) {
        if (viewerId == null || post == null) return false;
        if (viewerId.equals(post.getAuthorId())) return true;
        if (post.getGroupId() != null) {
            UserConnectionClient.GroupFeedVisibility group = loadGroupVisibility(List.of(post)).get(post.getGroupId());
            return group != null && (group.member() || group.publicGroup());
        }
        if (post.getPrivacy() == PostPrivacy.PUBLIC) return true;
        if (post.getPrivacy() == PostPrivacy.PRIVATE) return false;
        if (post.getPrivacy() == PostPrivacy.CUSTOM) return post.getAllowedUserIds() != null && post.getAllowedUserIds().contains(viewerId);
        if (post.getPrivacy() != PostPrivacy.FRIENDS) return false;
        try {
            ApiResponse<Map<String, Object>> response = userConnectionClient.getConnectionStatus(post.getAuthorId());
            Map<String, Object> data = response != null ? response.getData() : null;
            Object value = data != null ? data.get("friend") : null;
            if (value == null && data != null) value = data.get("isFriend");
            return value instanceof Boolean bool && bool;
        } catch (Exception e) {
            log.warn("Privacy verification failed between viewer {} and author {}: {}", viewerId, post.getAuthorId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks one page of posts with one friends-list request instead of making
     * a network request for every FRIENDS-only post.
     */
    public Set<UUID> visiblePostIds(Collection<Post> posts, UUID viewerId) {
        return visiblePostIds(posts, viewerId, true);
    }

    /**
     * Bảng tin chính (limitToJoinedGroups = true) chỉ có bài của các nhóm mình đã tham gia, giống Facebook: bài của nhóm
     * công khai khác xem ở Nhóm > Khám phá, trang nhóm, tìm kiếm hoặc Watch. Các nơi đó (limitToJoinedGroups = false)
     * vẫn thấy bài nhóm công khai; bài nhóm riêng tư thì chỉ thành viên thấy ở mọi nơi.
     */
    public Set<UUID> visiblePostIds(Collection<Post> posts, UUID viewerId, boolean limitToJoinedGroups) {
        Set<UUID> visible = new HashSet<>();
        if (posts == null) return visible;
        if (viewerId == null) {
            for (Post post : posts) {
                if (post != null && post.getPrivacy() == PostPrivacy.PUBLIC && post.getGroupId() == null) {
                    visible.add(post.getId());
                }
            }
            return visible;
        }

        boolean requiresFriendLookup = posts.stream().anyMatch(post ->
                post != null && post.getGroupId() == null && post.getPrivacy() == PostPrivacy.FRIENDS && !viewerId.equals(post.getAuthorId()));
        Set<UUID> friendIds = requiresFriendLookup ? loadFriendIds() : Set.of();
        Map<UUID, UserConnectionClient.GroupFeedVisibility> groupVisibility = loadGroupVisibility(posts);

        for (Post post : posts) {
            if (post == null) continue;
            if (viewerId.equals(post.getAuthorId())) {
                visible.add(post.getId());
                continue;
            }
            if (post.getGroupId() != null) {
                UserConnectionClient.GroupFeedVisibility group = groupVisibility.get(post.getGroupId());
                if (group == null) continue;

                // Private group posts are ONLY visible to members
                if (!group.publicGroup() && !group.member()) {
                    continue;
                }

                if (limitToJoinedGroups && !group.member()) {
                    continue;
                }

                visible.add(post.getId());
                continue;
            }
            if (post.getPrivacy() == PostPrivacy.PUBLIC
                    || (post.getPrivacy() == PostPrivacy.FRIENDS && friendIds.contains(post.getAuthorId()))
                    || (post.getPrivacy() == PostPrivacy.CUSTOM && post.getAllowedUserIds() != null
                    && post.getAllowedUserIds().contains(viewerId))) {
                visible.add(post.getId());
            }
        }
        return visible;
    }

    /** Mã mọi nhóm mà người xem là thành viên (rỗng nếu không hỏi được user-service). */
    public Set<UUID> joinedGroupIds() {
        try {
            ApiResponse<List<UUID>> response = userConnectionClient.getJoinedGroupIds();
            return response == null || response.getData() == null ? Set.of() : new HashSet<>(response.getData());
        } catch (Exception e) {
            log.warn("Could not load the joined groups of the viewer: {}", e.getMessage());
            return Set.of();
        }
    }

    /** Mã các nhóm mà người xem là thành viên trong số các bài đã cho (dùng để cộng điểm bài của nhóm mình tham gia). */
    public Set<UUID> memberGroupIds(Collection<Post> posts) {
        Set<UUID> ids = new HashSet<>();
        loadGroupVisibility(posts).forEach((groupId, visibility) -> {
            if (visibility.member()) ids.add(groupId);
        });
        return ids;
    }

    private Map<UUID, UserConnectionClient.GroupFeedVisibility> loadGroupVisibility(Collection<Post> posts) {
        List<UUID> groupIds = posts.stream().filter(post -> post != null && post.getGroupId() != null)
                .map(Post::getGroupId).distinct().toList();
        if (groupIds.isEmpty()) return Map.of();
        try {
            ApiResponse<List<UserConnectionClient.GroupFeedVisibility>> response = userConnectionClient.getGroupFeedVisibility(groupIds);
            List<UserConnectionClient.GroupFeedVisibility> data = response == null ? null : response.getData();
            if (data == null) return Map.of();
            Map<UUID, UserConnectionClient.GroupFeedVisibility> result = new HashMap<>();
            for (UserConnectionClient.GroupFeedVisibility entry : data) {
                if (entry != null && entry.groupId() != null) result.put(entry.groupId(), entry);
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not load group visibility for groups {}: {}", groupIds, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> loadFriendIds() {
        try {
            ApiResponse<Map<String, Object>> response = userConnectionClient.getFriends(0, 500);
            Map<String, Object> page = response != null ? response.getData() : null;
            Object content = page != null ? page.get("content") : null;
            if (!(content instanceof List<?> entries)) return Set.of();

            Set<UUID> friendIds = new HashSet<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> friend)) continue;
                Object userId = friend.get("userId");
                if (userId == null) continue;
                try {
                    friendIds.add(UUID.fromString(String.valueOf(userId)));
                } catch (IllegalArgumentException ignored) { }
            }
            return friendIds;
        } catch (Exception ignored) {
            return Set.of(); // restricted posts remain hidden when verification fails
        }
    }
}
