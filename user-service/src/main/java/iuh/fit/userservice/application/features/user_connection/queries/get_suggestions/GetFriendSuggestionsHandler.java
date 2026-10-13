package iuh.fit.userservice.application.features.user_connection.queries.get_suggestions;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.application.service.suggestion.FriendSuggestionCache;
import iuh.fit.userservice.application.service.suggestion.FriendSuggestionEngine;
import iuh.fit.userservice.application.service.suggestion.FriendSuggestionEngine.RankedCandidate;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Những người bạn có thể biết": xếp hạng theo bạn chung (Adamic–Adar), nhóm chung, quan hệ theo dõi và tín hiệu hồ sơ,
 * xem {@link FriendSuggestionEngine}. Kết quả xếp hạng được cache ngắn hạn trong Redis, phân trang trên danh sách đã xếp hạng.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetFriendSuggestionsHandler {

    FriendSuggestionEngine suggestionEngine;
    FriendSuggestionCache suggestionCache;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional(readOnly = true)
    public PagedResponse<FriendSuggestionResult> handle(GetFriendSuggestionsQuery query) {
        UUID currentUserId = query.getCurrentUserId();

        List<RankedCandidate> ranked = suggestionCache.get(currentUserId).orElse(null);
        if (ranked == null) {
            ranked = suggestionEngine.rank(currentUserId);
            suggestionCache.put(currentUserId, ranked);
        }

        int page = query.getFilter() != null ? Math.max(0, query.getFilter().getPage() - 1) : 0;
        int size = query.getFilter() != null && query.getFilter().getSize() > 0 ? query.getFilter().getSize() : 20;

        int totalElements = ranked.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<RankedCandidate> pageItems = ranked.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        if (pageItems.isEmpty()) {
            return PagedResponse.<FriendSuggestionResult>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .last(true)
                    .build();
        }

        Map<UUID, UserProfile> profileMap = userProfileRepository
                .findByUserIdIn(pageItems.stream().map(RankedCandidate::userId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        List<FriendSuggestionResult> results = pageItems.stream()
                .map(c -> userConnectionApplicationMapper.toSuggestionResult(
                        c.userId(), profileMap.get(c.userId()), c.mutualCount(), c.score(), c.reasons()))
                .toList();

        return PagedResponse.<FriendSuggestionResult>builder()
                .content(results)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }
}
