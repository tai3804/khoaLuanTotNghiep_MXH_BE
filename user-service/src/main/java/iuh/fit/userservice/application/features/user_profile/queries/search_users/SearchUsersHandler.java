package iuh.fit.userservice.application.features.user_profile.queries.search_users;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.application.service.SearchText;
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
import java.util.stream.Stream;

/**
 * Tìm người dùng. Hai đường, theo thứ tự:
 * <ol>
 *   <li><b>Theo tên</b> (bỏ dấu, hoa thường, xếp theo độ khớp): gõ "Tài" ra người tên Tài. Chỉ là truy vấn DB, không gọi Gemini.</li>
 *   <li><b>Theo ngữ nghĩa</b> (embedding): chỉ khi không ai khớp tên, ví dụ "người thích lập trình". Đây là lúc duy nhất
 *       cần gọi Gemini.</li>
 * </ol>
 * Trước đây đường ngữ nghĩa chạy trước và trả về luôn khi có bất kỳ kết quả nào, nên tên người không bao giờ được so khớp
 * (gõ "Tài" lại ra một người không liên quan), và mỗi lần gõ đều tốn một lần gọi Gemini.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchUsersHandler {

    /** Số ứng viên tối đa lấy từ DB để xếp hạng theo độ khớp tên. */
    private static final int NAME_CANDIDATES = 200;

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
        if (keyword.isBlank()) {
            return userProfileApplicationMapper.toPagedResult(userProfileRepository.searchUsers(keyword, pageable));
        }

        List<UserProfile> byName = rankByName(keyword);
        if (!byName.isEmpty()) {
            return userProfileApplicationMapper.toPagedResult(pageOf(byName, pageable));
        }

        List<UserProfile> semantic = semanticMatches(keyword, pageSize);
        return userProfileApplicationMapper.toPagedResult(pageOf(semantic, pageable));
    }

    /** Người có tên khớp từ khoá, khớp tốt nhất lên đầu (trùng cả tên, nguyên từ, đầu từ, bên trong). */
    private List<UserProfile> rankByName(String keyword) {
        String foldedQuery = SearchText.fold(keyword);
        List<UserProfile> candidates = userProfileRepository.searchByName(SearchText.likePattern(keyword), NAME_CANDIDATES);
        if (candidates.isEmpty()) return List.of();

        record Scored(UserProfile profile, String name, int rank) {
        }
        return candidates.stream()
                .map(p -> {
                    String name = SearchText.fold(String.join(" ",
                            Stream.of(p.getLastName(), p.getMiddleName(), p.getFirstName()).filter(Objects::nonNull).toList()));
                    return new Scored(p, name, SearchText.nameRank(name, foldedQuery));
                })
                .sorted(Comparator.comparingInt(Scored::rank).thenComparingInt(s -> s.name().length()).thenComparing(Scored::name))
                .map(Scored::profile)
                .toList();
    }

    /** Tìm theo ý nghĩa (bio, sở thích, lĩnh vực) bằng vector; rỗng nếu không tạo được embedding hoặc không đủ giống. */
    private List<UserProfile> semanticMatches(String keyword, int pageSize) {
        try {
            List<Float> queryVector = userEmbeddingService.getQueryEmbedding(keyword);
            if (queryVector == null || queryVector.isEmpty()) return List.of();

            List<UserEmbeddingService.UserVectorMatch> matches = userEmbeddingService.searchSimilar(queryVector, pageSize * 2, 0.35);
            if (matches == null || matches.isEmpty()) return List.of();

            List<UUID> matchedUserIds = matches.stream().map(UserEmbeddingService.UserVectorMatch::userId).toList();
            Map<UUID, Double> scoreMap = matches.stream()
                    .collect(Collectors.toMap(UserEmbeddingService.UserVectorMatch::userId, UserEmbeddingService.UserVectorMatch::similarity, (a, b) -> a));
            return userProfileRepository.findByUserIdIn(matchedUserIds).stream()
                    .filter(p -> p != null && !p.isDeleted())
                    .sorted((p1, p2) -> Double.compare(scoreMap.getOrDefault(p2.getUserId(), 0.0), scoreMap.getOrDefault(p1.getUserId(), 0.0)))
                    .toList();
        } catch (Exception e) {
            log.warn("User vector search failed for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private static Page<UserProfile> pageOf(List<UserProfile> all, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
