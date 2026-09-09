package iuh.fit.userservice.application.features.user_presence.commands.update_presence;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdatePresenceCommandHandler {

    UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(UpdatePresenceCommand command) {
        Optional<UserProfile> profileOpt = userProfileRepository.findByUserId(command.getUserId());
        if (profileOpt.isEmpty()) {
            profileOpt = userProfileRepository.findById(command.getUserId());
        }

        if (profileOpt.isPresent()) {
            UserProfile profile = profileOpt.get();
            profile.setIsOnline(command.getIsOnline() != null ? command.getIsOnline() : true);
            profile.setLastActiveAt(Instant.now());
            userProfileRepository.save(profile);
        }
    }
}
