package iuh.fit.userservice.application.features.user_connection.queries.get_connections;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetConnectionsQueryHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public PagedResponse<UserConnectionResult> handle(GetConnectionsQuery query) {
        int safePage = Math.max(0, query.getPage());
        int safeSize = Math.max(1, Math.min(query.getSize(), 100));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        Page<UserConnection> pageResult;

        String mode = query.getMode() != null ? query.getMode().toUpperCase() : "FRIENDS";

        switch (mode) {
            case "FOLLOWERS":
                pageResult = userConnectionRepository.findFollowersOfUser(query.getUserId(), pageable);
                break;
            case "FOLLOWING":
                pageResult = userConnectionRepository.findFollowingOfUser(query.getUserId(), pageable);
                break;
            case "PENDING_REQUESTS":
                pageResult = userConnectionRepository.findPendingFriendRequestsForUser(query.getUserId(), pageable);
                break;
            case "FRIENDS":
            default:
                pageResult = userConnectionRepository.findFriendsOfUser(query.getUserId(), pageable);
                break;
        }

        if (pageResult == null || pageResult.isEmpty()) {
            return PagedResponse.<UserConnectionResult>builder()
                    .content(Collections.emptyList())
                    .page(safePage)
                    .size(safeSize)
                    .totalElements(pageResult != null ? pageResult.getTotalElements() : 0)
                    .totalPages(pageResult != null ? pageResult.getTotalPages() : 0)
                    .last(true)
                    .build();
        }

        List<UUID> otherUserIds = pageResult.getContent().stream()
                .map(conn -> resolveOtherUserId(conn, query.getUserId(), mode))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, UserProfile> profileMap = otherUserIds.isEmpty() ? Collections.emptyMap() :
                userProfileRepository.findByUserIdIn(otherUserIds).stream()
                        .collect(Collectors.toMap(UserProfile::getUserId, p -> p, (a, b) -> a));

        List<UserConnectionResult> content = pageResult.getContent().stream().map(conn -> {
            UUID otherUserId = resolveOtherUserId(conn, query.getUserId(), mode);
            UserProfile profile = otherUserId != null ? profileMap.get(otherUserId) : null;

            String fullName = null;
            if (profile != null) {
                fullName = Stream.of(profile.getLastName(), profile.getMiddleName(), profile.getFirstName())
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(" "));
            }

            LocalDateTime lastActiveLocalDateTime = null;
            if (profile != null && profile.getLastActiveAt() != null) {
                lastActiveLocalDateTime = LocalDateTime.ofInstant(profile.getLastActiveAt(), ZoneId.systemDefault());
            }

            return UserConnectionResult.builder()
                    .id(conn.getId())
                    .requesterId(conn.getRequesterId())
                    .targetId(conn.getTargetId())
                    .type(conn.getType())
                    .status(conn.getStatus())
                    .userId(otherUserId)
                    .fullName(fullName)
                    .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                    .isOnline(profile != null && Boolean.TRUE.equals(profile.getIsOnline()))
                    .lastActiveAt(lastActiveLocalDateTime)
                    .build();
        }).toList();

        return PagedResponse.<UserConnectionResult>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    private UUID resolveOtherUserId(UserConnection conn, UUID currentUserId, String mode) {
        if (conn == null) return null;
        if ("FOLLOWERS".equals(mode) || "PENDING_REQUESTS".equals(mode)) {
            return conn.getRequesterId();
        } else if ("FOLLOWING".equals(mode)) {
            return conn.getTargetId();
        } else {
            return conn.getRequesterId().equals(currentUserId) ? conn.getTargetId() : conn.getRequesterId();
        }
    }
}
