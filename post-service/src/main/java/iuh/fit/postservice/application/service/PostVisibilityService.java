package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostVisibilityService {
    private final UserConnectionClient userConnectionClient;

    public boolean canView(Post post, UUID viewerId) {
        if (viewerId == null || post == null) return false;
        if (post.getGroupId() != null && !viewerId.equals(post.getAuthorId())) {
            UserConnectionClient.GroupFeedVisibility group = loadGroupVisibility(List.of(post)).get(post.getGroupId());
            if (group == null || (!group.member() && !group.publicGroup())) return false;
        }
        if (viewerId.equals(post.getAuthorId()) || post.getPrivacy() == PostPrivacy.PUBLIC) return true;
        if (post.getPrivacy() == PostPrivacy.PRIVATE) return false;
        if (post.getPrivacy() == PostPrivacy.CUSTOM) return post.getAllowedUserIds() != null && post.getAllowedUserIds().contains(viewerId);
        if (post.getPrivacy() != PostPrivacy.FRIENDS) return false;
        try {
            ApiResponse<Map<String, Object>> response = userConnectionClient.getConnectionStatus(post.getAuthorId());
            Map<String, Object> data = response != null ? response.getData() : null;
            Object value = data != null ? data.get("friend") : null;
            if (value == null && data != null) value = data.get("isFriend");
            return value instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false; // privacy must fail closed if the relationship cannot be verified
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
     * Main feed only includes joined-group posts plus a small number of public
     * group recommendations. Group-detail pages opt out of that recommendation cap.
     */
    public Set<UUID> visiblePostIds(Collection<Post> posts, UUID viewerId, boolean limitPublicGroupRecommendations) {
        Set<UUID> visible = new HashSet<>();
        if (posts == null || viewerId == null) return visible;

        boolean requiresFriendLookup = posts.stream().anyMatch(post ->
                post != null && post.getPrivacy() == PostPrivacy.FRIENDS && !viewerId.equals(post.getAuthorId()));
        Set<UUID> friendIds = requiresFriendLookup ? loadFriendIds() : Set.of();
        Map<UUID, UserConnectionClient.GroupFeedVisibility> groupVisibility = loadGroupVisibility(posts);
        int publicGroupRecommendations = 0;

        for (Post post : posts) {
            if (post == null) continue;
            if (post.getGroupId() != null && !viewerId.equals(post.getAuthorId())) {
                UserConnectionClient.GroupFeedVisibility group = groupVisibility.get(post.getGroupId());
                if (group == null || (!group.member() && !group.publicGroup())) continue;
                if (!group.member() && limitPublicGroupRecommendations && publicGroupRecommendations >= 3) continue;
                if (!group.member() && limitPublicGroupRecommendations) publicGroupRecommendations++;
            }
            if (viewerId.equals(post.getAuthorId()) || post.getPrivacy() == PostPrivacy.PUBLIC
                    || (post.getPrivacy() == PostPrivacy.FRIENDS && friendIds.contains(post.getAuthorId()))
                    || (post.getPrivacy() == PostPrivacy.CUSTOM && post.getAllowedUserIds() != null
                    && post.getAllowedUserIds().contains(viewerId))) {
                visible.add(post.getId());
            }
        }
        return visible;
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
        } catch (Exception ignored) {
            return Map.of(); // group posts fail closed if membership cannot be checked
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
