package iuh.fit.userservice.application.features.user_profile.queries.search_users;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.application.service.UserEmbeddingService;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchUsersHandler {

    UserProfileRepository userProfileRepository;
    UserProfileApplicationMapper userProfileApplicationMapper;
    UserEmbeddingService userEmbeddingService;

    @Transactional(readOnly = true)
    public PagedResponse<GetUserProfileResult> handle(SearchUsersQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageIndex = Math.max(0, filter.getPage() - 1);
        int pageSize = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String keyword = filter.getKeyword() != null ? filter.getKeyword().trim() : "";

        // Thử tìm kiếm ngữ nghĩa nếu từ khóa đủ dài (chuyên môn, sở thích, bio)
        if (!keyword.isBlank()) {
            try {
                List<Float> queryVector = userEmbeddingService.getQueryEmbedding(keyword);
                if (queryVector != null && !queryVector.isEmpty()) {
                    List<UserEmbeddingService.UserVectorMatch> matches = userEmbeddingService.searchSimilar(queryVector, pageSize * 2, 0.35);
                    if (matches != null && !matches.isEmpty()) {
                        List<UUID> matchedUserIds = matches.stream().map(UserEmbeddingService.UserVectorMatch::userId).toList();
                        Map<UUID, Double> scoreMap = matches.stream()
                                .collect(Collectors.toMap(UserEmbeddingService.UserVectorMatch::userId, UserEmbeddingService.UserVectorMatch::similarity, (a, b) -> a));

                        List<UserProfile> profiles = userProfileRepository.findByUserIdIn(matchedUserIds).stream()
                                .filter(p -> p != null && !p.isDeleted())
                                .sorted((p1, p2) -> Double.compare(scoreMap.getOrDefault(p2.getUserId(), 0.0), scoreMap.getOrDefault(p1.getUserId(), 0.0)))
                                .toList();

                        if (!profiles.isEmpty()) {
                            int total = profiles.size();
                            int from = Math.min(pageIndex * pageSize, total);
                            int to = Math.min(from + pageSize, total);
                            List<UserProfile> pagedProfiles = profiles.subList(from, to);
                            Page<UserProfile> vectorPage = new PageImpl<>(pagedProfiles, pageable, total);
                            return userProfileApplicationMapper.toPagedResult(vectorPage);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("User vector search failed for '{}', falling back to keyword search: {}", keyword, e.getMessage());
            }
        }

        Page<UserProfile> profilePage = userProfileRepository.searchUsers(keyword, pageable);
        return userProfileApplicationMapper.toPagedResult(profilePage);
    }
}
