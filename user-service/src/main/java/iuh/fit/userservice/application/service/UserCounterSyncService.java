package iuh.fit.userservice.application.service;

import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserCounterSyncService {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;

    @Transactional
    public void syncCounters(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            long friends = userConnectionRepository.countAcceptedFriends(userId);
            long followers = userConnectionRepository.countAcceptedFollowers(userId);
            long following = userConnectionRepository.countAcceptedFollowing(userId);

            userProfileRepository.findByUserId(userId).ifPresent(profile -> {
                profile.setFriendCount(friends);
                profile.setFollowerCount(followers);
                profile.setFollowingCount(following);
                userProfileRepository.save(profile);
            });
        } catch (Exception e) {
            log.warn("Failed to sync counters for user {}: {}", userId, e.getMessage());
        }
    }
}

