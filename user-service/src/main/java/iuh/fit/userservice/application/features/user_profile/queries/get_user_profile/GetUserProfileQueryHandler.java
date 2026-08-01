package iuh.fit.userservice.application.features.user_profile.queries.get_user_profile;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserProfileQueryHandler {

    UserProfileRepository userProfileRepository;
    UserProfileApplicationMapper userProfileApplicationMapper;

    @Transactional(readOnly = true)
    public GetUserProfileResult handle(GetUserProfileQuery query) {
        UserProfile userProfile = userProfileRepository.findByUserId(query.getUserId())
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND));

        return userProfileApplicationMapper.toQueryResult(userProfile);
    }
}
