package iuh.fit.userservice.application.features.user_connection.commands.unfollow_user;

import iuh.fit.userservice.application.service.UserCounterSyncService;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnfollowUserCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserCounterSyncService userCounterSyncService;

    @Transactional
    public void handle(UnfollowUserCommand command) {
        userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(command.getFollowerId(), command.getTargetId(), ConnectionType.FOLLOW)
                .ifPresent(conn -> {
                    userConnectionRepository.delete(conn);
                    userCounterSyncService.syncCounters(command.getFollowerId());
                    userCounterSyncService.syncCounters(command.getTargetId());
                });
    }
}
