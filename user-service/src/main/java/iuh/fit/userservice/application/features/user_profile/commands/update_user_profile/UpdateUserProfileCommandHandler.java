package iuh.fit.userservice.application.features.user_profile.commands.update_user_profile;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.UserAvatarUpdatedEvent;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserProfileApplicationMapper;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdateUserProfileCommandHandler {

    UserProfileRepository userProfileRepository;
    UserProfileApplicationMapper userProfileApplicationMapper;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public UpdateUserProfileResult handle(UpdateUserProfileCommand command) {
        UserProfile userProfile = userProfileRepository.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND));

        String oldAvatar = userProfile.getAvatarUrl();
        String oldCover = userProfile.getCoverImageUrl();

        userProfileApplicationMapper.updateEntityFromCommand(command, userProfile);
        UserProfile savedProfile = userProfileRepository.save(userProfile);

        String newAvatar = savedProfile.getAvatarUrl();
        String newCover = savedProfile.getCoverImageUrl();

        if ((newAvatar != null && !newAvatar.equals(oldAvatar)) || (newCover != null && !newCover.equals(oldCover))) {
            try {
                UserAvatarUpdatedEvent event = userProfileApplicationMapper.toAvatarUpdatedEvent(
                        command.getUserId(), oldAvatar, newAvatar, oldCover, newCover
                );
                kafkaTemplate.send("user.avatar.updated", event);
                log.info("Published UserAvatarUpdatedEvent for userId: {}", command.getUserId());
            } catch (Exception e) {
                log.error("Failed to publish UserAvatarUpdatedEvent for userId {}: {}", command.getUserId(), e.getMessage());
            }
        }

        return userProfileApplicationMapper.toUpdateResult(savedProfile);
    }
}
