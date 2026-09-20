package iuh.fit.postservice.application.service;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
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
        Set<UUID> visible = new HashSet<>();
        if (posts == null || viewerId == null) return visible;

        boolean requiresFriendLookup = posts.stream().anyMatch(post ->
                post != null && post.getPrivacy() == PostPrivacy.FRIENDS && !viewerId.equals(post.getAuthorId()));
        Set<UUID> friendIds = requiresFriendLookup ? loadFriendIds() : Set.of();

        for (Post post : posts) {
            if (post == null) continue;
            if (viewerId.equals(post.getAuthorId()) || post.getPrivacy() == PostPrivacy.PUBLIC
                    || (post.getPrivacy() == PostPrivacy.FRIENDS && friendIds.contains(post.getAuthorId()))
                    || (post.getPrivacy() == PostPrivacy.CUSTOM && post.getAllowedUserIds() != null
                    && post.getAllowedUserIds().contains(viewerId))) {
                visible.add(post.getId());
            }
        }
        return visible;
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
