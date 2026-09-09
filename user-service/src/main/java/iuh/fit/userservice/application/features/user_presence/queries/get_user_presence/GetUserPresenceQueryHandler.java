package iuh.fit.userservice.application.features.user_presence.queries.get_user_presence;

import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserPresenceQueryHandler {

    UserProfileRepository userProfileRepository;
    private static final Duration ONLINE_TIMEOUT = Duration.ofMinutes(5);

    @Transactional(readOnly = true)
    public List<UserPresenceResult> handle(GetUserPresenceQuery query) {
        List<UUID> userIds = new ArrayList<>();
        if (query.getTargetUserId() != null) {
            userIds.add(query.getTargetUserId());
        }
        if (query.getTargetUserIds() != null && !query.getTargetUserIds().isEmpty()) {
            userIds.addAll(query.getTargetUserIds());
        }

        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserProfile> profiles = userProfileRepository.findByUserIdIn(userIds);
        Instant now = Instant.now();

        return profiles.stream().map(profile -> {
            boolean rawOnline = Boolean.TRUE.equals(profile.getIsOnline());
            Instant lastActive = profile.getLastActiveAt();

            // Dynamic online status calculation: must have sent heartbeat within last 5 minutes
            boolean computedOnline = rawOnline && (lastActive != null && Duration.between(lastActive, now).compareTo(ONLINE_TIMEOUT) <= 0);

            return UserPresenceResult.builder()
                    .userId(profile.getUserId())
                    .isOnline(computedOnline)
                    .lastActiveAt(lastActive)
                    .build();
        }).toList();
    }
}
