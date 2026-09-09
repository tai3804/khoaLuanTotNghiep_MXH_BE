package iuh.fit.userservice.application.features.user_connection.queries.get_suggestions;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserBlockRepository;
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
public class GetFriendSuggestionsHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserBlockRepository userBlockRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional(readOnly = true)
    public PagedResponse<FriendSuggestionResult> handle(GetFriendSuggestionsQuery query) {
        UUID currentUserId = query.getCurrentUserId();

        // 1. Get current user's accepted friends
        Set<UUID> myFriendIds = getAcceptedFriendIds(currentUserId);

        // 2. Get blocked user IDs (both blocked by me and blocked me)
        Set<UUID> blockedUserIds = userBlockRepository.findAllBlockedUserIdsRelatedTo(currentUserId);

        // Excluded set: self + current friends + blocked users
        Set<UUID> excludedUserIds = new HashSet<>(myFriendIds);
        excludedUserIds.add(currentUserId);
        excludedUserIds.addAll(blockedUserIds);

        // 3. Count mutual friends for candidates (Friends of Friends)
        Map<UUID, Long> candidateMutualCountMap = new HashMap<>();
        if (!myFriendIds.isEmpty()) {
            List<UserConnection> fofConnections = userConnectionRepository.findAllAcceptedFriendshipsForUserIds(myFriendIds);
            for (UserConnection c : fofConnections) {
                UUID fofCandidate = myFriendIds.contains(c.getRequesterId()) ? c.getTargetId() : c.getRequesterId();
                if (!excludedUserIds.contains(fofCandidate)) {
                    candidateMutualCountMap.put(fofCandidate, candidateMutualCountMap.getOrDefault(fofCandidate, 0L) + 1);
                }
            }
        }

        // Sort candidates by mutual friend count descending
        List<UUID> candidateIds = candidateMutualCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 4. Fallback if candidates < 20: suggest active users
        if (candidateIds.size() < 20) {
            List<UserProfile> recentUsers = userProfileRepository.findAll();
            for (UserProfile p : recentUsers) {
                if (!excludedUserIds.contains(p.getUserId()) && !candidateMutualCountMap.containsKey(p.getUserId())) {
                    candidateIds.add(p.getUserId());
                    candidateMutualCountMap.put(p.getUserId(), 0L);
                }
            }
        }

        int page = query.getFilter() != null ? query.getFilter().getPage() : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;

        int totalElements = candidateIds.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<UUID> pagedIds = candidateIds.subList(fromIndex, toIndex);

        if (pagedIds.isEmpty()) {
            return PagedResponse.<FriendSuggestionResult>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        Map<UUID, UserProfile> profileMap = userProfileRepository.findByUserIdIn(pagedIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        List<FriendSuggestionResult> results = pagedIds.stream()
                .map(userId -> userConnectionApplicationMapper.toSuggestionResult(
                        userId, profileMap.get(userId), candidateMutualCountMap.getOrDefault(userId, 0L)))
                .toList();

        int totalPages = (int) Math.ceil((double) totalElements / size);

        return PagedResponse.<FriendSuggestionResult>builder()
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
