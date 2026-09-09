package iuh.fit.userservice.application.features.user_connection.queries.get_mutual_friends;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.UserConnectionResult;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetMutualFriendsHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional(readOnly = true)
    public PagedResponse<UserConnectionResult> handle(GetMutualFriendsQuery query) {
        Set<UUID> myFriendIds = getAcceptedFriendIds(query.getCurrentUserId());
        Set<UUID> targetFriendIds = getAcceptedFriendIds(query.getTargetUserId());

        // Intersection of friends
        Set<UUID> mutualIds = new HashSet<>(myFriendIds);
        mutualIds.retainAll(targetFriendIds);

        List<UUID> mutualList = new ArrayList<>(mutualIds);

        int page = query.getFilter() != null ? query.getFilter().getPage() : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;

        int totalElements = mutualList.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<UUID> pagedIds = mutualList.subList(fromIndex, toIndex);

        if (pagedIds.isEmpty()) {
            return PagedResponse.<UserConnectionResult>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages((int) Math.ceil((double) totalElements / size))
                    .last(true)
                    .build();
        }

        Map<UUID, UserProfile> profileMap = userProfileRepository.findByUserIdIn(pagedIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        List<UserConnectionResult> results = pagedIds.stream()
                .map(userId -> userConnectionApplicationMapper.toResult(userId, profileMap.get(userId)))
                .toList();

        int totalPages = (int) Math.ceil((double) totalElements / size);

        return PagedResponse.<UserConnectionResult>builder()
                .content(results)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    private Set<UUID> getAcceptedFriendIds(UUID userId) {
        List<UserConnection> connections = userConnectionRepository.findAllAcceptedFriendships(userId);
        Set<UUID> friendIds = new HashSet<>();
        for (UserConnection c : connections) {
            if (c.getRequesterId().equals(userId)) {
                friendIds.add(c.getTargetId());
            } else {
                friendIds.add(c.getRequesterId());
            }
        }
        return friendIds;
    }
}
