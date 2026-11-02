package iuh.fit.userservice.application.features.user_connection.commands.reject_friend_request;

import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RejectFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(RejectFriendRequestCommand command) {
        if (command == null || command.getUserId() == null || command.getRequesterId() == null) {
            return;
        }

        UUID rawUserId = command.getUserId();
        UUID rawRequesterId = command.getRequesterId();

        UUID resolvedUserId = resolveUserId(rawUserId);
        UUID resolvedRequesterId = resolveUserId(rawRequesterId);

        UserConnection friendConn = userConnectionRepository.findById(rawRequesterId)
                .or(() -> userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                        resolvedRequesterId, resolvedUserId, ConnectionType.FRIEND
                ))
                .or(() -> userConnectionRepository.findConnectionBetween(resolvedRequesterId, resolvedUserId, ConnectionType.FRIEND))
                .or(() -> userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                        rawRequesterId, rawUserId, ConnectionType.FRIEND
                ))
                .orElse(null);

        if (friendConn != null) {
            friendConn.setStatus(ConnectionStatus.REJECTED);
            userConnectionRepository.save(friendConn);
            log.info("Rejected friend connection between {} and {}", resolvedRequesterId, resolvedUserId);
        } else {
            log.info("No active friend request found to reject between {} and {}", resolvedRequesterId, resolvedUserId);
        }
    }

    private UUID resolveUserId(UUID id) {
        if (id == null) return null;
        Optional<UserProfile> p = userProfileRepository.findByUserId(id);
        if (p.isPresent()) return p.get().getUserId();
        p = userProfileRepository.findById(id);
        if (p.isPresent()) return p.get().getUserId();
        return id;
    }
}
