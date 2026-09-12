package iuh.fit.userservice.application.features.user_profile.queries.get_user_profile;

import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserProfileQueryHandler {

    UserProfileRepository userProfileRepository;
    UserProfileApplicationMapper userProfileApplicationMapper;

    @Transactional
    public GetUserProfileResult handle(GetUserProfileQuery query) {
        UserProfile userProfile = userProfileRepository.findByUserId(query.getUserId())
                .or(() -> userProfileRepository.findById(query.getUserId()))
                .orElseGet(() -> {
                    log.warn("UserProfile not found for userId: {}. Auto-creating default UserProfile.", query.getUserId());
                    return userProfileRepository.save(userProfileApplicationMapper.toDefaultEntity(query.getUserId()));
                });

        return userProfileApplicationMapper.toQueryResult(userProfile);
    }
}
