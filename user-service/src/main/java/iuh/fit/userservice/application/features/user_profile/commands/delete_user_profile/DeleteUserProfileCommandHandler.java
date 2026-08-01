package iuh.fit.userservice.application.features.user_profile.commands.delete_user_profile;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
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
public class DeleteUserProfileCommandHandler {

    UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(DeleteUserProfileCommand command) {
        UserProfile userProfile = userProfileRepository.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND));

        userProfileRepository.delete(userProfile);
    }
}
