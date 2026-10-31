package iuh.fit.userservice.application.features.user_profile.queries.get_user_profile;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
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
    JwtUtil jwtUtil;

    @Transactional
    public GetUserProfileResult handle(GetUserProfileQuery query) {
        UserProfile userProfile = userProfileRepository.findByUserId(query.getUserId())
                .or(() -> userProfileRepository.findById(query.getUserId()))
                .orElseGet(() -> {
                    String currentUserId = null;
                    try {
                        currentUserId = jwtUtil.getCurrentUserId();
                    } catch (Exception ignored) {}

                    if (currentUserId != null && currentUserId.equalsIgnoreCase(query.getUserId().toString())) {
                        log.warn("UserProfile not found for current user: {}. Auto-creating UserProfile with available claims.", query.getUserId());
                        String firstName = null;
                        String lastName = null;
                        String middleName = null;
                        try {
                            firstName = jwtUtil.getClaim("firstName");
                            lastName = jwtUtil.getClaim("lastName");
                            middleName = jwtUtil.getClaim("middleName");
                        } catch (Exception e) {
                            log.debug("Could not extract user name claims from JWT: {}", e.getMessage());
                        }
                        return userProfileRepository.save(userProfileApplicationMapper.toDefaultEntity(query.getUserId(), firstName, lastName, middleName));
                    }
                    throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
                });

        return userProfileApplicationMapper.toQueryResult(userProfile);
    }
}
