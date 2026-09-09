package iuh.fit.userservice.application.features.user_profile.queries.search_users;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchUsersHandler {

    UserProfileRepository userProfileRepository;
    UserProfileApplicationMapper userProfileApplicationMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetUserProfileResult> handle(SearchUsersQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageIndex = Math.max(0, filter.getPage() - 1);
        int pageSize = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String keyword = filter.getKeyword() != null ? filter.getKeyword().trim() : "";
        Page<UserProfile> profilePage = userProfileRepository.searchUsers(keyword, pageable);

        return userProfileApplicationMapper.toPagedResult(profilePage);
    }
}
