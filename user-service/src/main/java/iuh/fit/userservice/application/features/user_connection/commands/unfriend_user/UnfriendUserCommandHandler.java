package iuh.fit.userservice.application.features.user_connection.commands.unfriend_user;

import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnfriendUserCommandHandler {

    UserConnectionRepository userConnectionRepository;

    @Transactional
    public void handle(UnfriendUserCommand command) {
        UUID userId = command.getUserId();
        UUID friendParamId = command.getFriendId();

        if (userId == null || friendParamId == null) {
            log.warn("Unfriend called with null parameters: userId={}, friendParamId={}", userId, friendParamId);
            return;
        }

        UUID targetUserId = friendParamId;

        // 1. If friendParamId is a connection record ID, resolve the real friend's userId
        Optional<UserConnection> connOpt = userConnectionRepository.findById(friendParamId);
        if (connOpt.isPresent()) {
            UserConnection conn = connOpt.get();
            targetUserId = conn.getRequesterId().equals(userId) ? conn.getTargetId() : conn.getRequesterId();
        }

        // 2. Find all connections between userId and targetUserId (FRIEND and mutual FOLLOW)
        List<UserConnection> connections = userConnectionRepository.findAllConnectionsBetween(userId, targetUserId);

        // 3. Delete all connections to completely unfriend both sides (A and B no longer friends or following)
        if (!connections.isEmpty()) {
            userConnectionRepository.deleteAll(connections);
            log.info("Successfully unfriended both sides between user {} and user {}. Removed {} connection rows.",
                    userId, targetUserId, connections.size());
        } else if (connOpt.isPresent()) {
            userConnectionRepository.delete(connOpt.get());
            log.info("Deleted single connection row by ID: {}", friendParamId);
        } else {
            log.warn("No active connections found to unfriend between user {} and {}", userId, targetUserId);
        }
    }
}

