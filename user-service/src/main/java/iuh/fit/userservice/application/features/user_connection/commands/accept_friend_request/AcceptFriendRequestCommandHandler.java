package iuh.fit.userservice.application.features.user_connection.commands.accept_friend_request;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.application.service.UserCounterSyncService;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AcceptFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;
    UserCounterSyncService userCounterSyncService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void handle(AcceptFriendRequestCommand command) {
        if (command == null || command.getUserId() == null || command.getRequesterId() == null) {
            throw new BusinessException(UserServiceErrorCode.INVALID_REQUEST);
        }

        UUID rawUserId = command.getUserId();
        UUID rawRequesterId = command.getRequesterId();

        // 1. Resolve real userId (Account ID) for current user
        UUID resolvedUserId = resolveUserId(rawUserId);

        // 2. Resolve real requesterId (could be connectionId, profileId, or userId)
        UUID resolvedRequesterId = resolveUserId(rawRequesterId);

        // 3. Try to locate existing friend connection
        UserConnection friendConn = null;

        // 3a. Direct check if rawRequesterId is a UserConnection primary key
        Optional<UserConnection> connById = userConnectionRepository.findById(rawRequesterId);
        if (connById.isPresent()) {
            friendConn = connById.get();
        }

        // 3b. Search between resolvedUserId and resolvedRequesterId
        if (friendConn == null) {
            friendConn = userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                    resolvedRequesterId, resolvedUserId, ConnectionType.FRIEND
            ).or(() -> userConnectionRepository.findConnectionBetween(resolvedRequesterId, resolvedUserId, ConnectionType.FRIEND))
             .or(() -> userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                    resolvedUserId, resolvedRequesterId, ConnectionType.FRIEND
             )).orElse(null);
        }

        // 3c. Fallback search with raw IDs in case they were stored un-normalized
        if (friendConn == null) {
            friendConn = userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                    rawRequesterId, rawUserId, ConnectionType.FRIEND
            ).or(() -> userConnectionRepository.findConnectionBetween(rawRequesterId, rawUserId, ConnectionType.FRIEND))
             .or(() -> userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                    rawUserId, rawRequesterId, ConnectionType.FRIEND
             )).orElse(null);
        }

        UUID finalRequesterId = resolvedRequesterId;

        // 4. If connection doesn't exist, create an accepted friend connection
        if (friendConn == null) {
            log.info("No prior friend connection found between {} and {}. Creating new ACCEPTED friend connection.",
                    resolvedRequesterId, resolvedUserId);
            friendConn = userConnectionApplicationMapper.toEntity(
                    resolvedRequesterId, resolvedUserId, ConnectionType.FRIEND, ConnectionStatus.ACCEPTED
            );
            userConnectionRepository.save(friendConn);
        } else {
            // Check authorization: current user must be one of the participants
            boolean isParticipant = friendConn.getTargetId().equals(resolvedUserId)
                    || friendConn.getRequesterId().equals(resolvedUserId)
                    || friendConn.getTargetId().equals(rawUserId)
                    || friendConn.getRequesterId().equals(rawUserId);

            if (!isParticipant) {
                throw new BusinessException(UserServiceErrorCode.FORBIDDEN);
            }

            finalRequesterId = (friendConn.getRequesterId().equals(resolvedUserId) || friendConn.getRequesterId().equals(rawUserId))
                    ? friendConn.getTargetId()
                    : friendConn.getRequesterId();

            if (friendConn.getStatus() == ConnectionStatus.ACCEPTED) {
                log.info("Friend connection between {} and {} is already ACCEPTED.", finalRequesterId, resolvedUserId);
            } else {
                friendConn.setStatus(ConnectionStatus.ACCEPTED);
                userConnectionRepository.save(friendConn);
            }
        }

        finalRequesterId = resolveUserId(finalRequesterId);

        // 5. Ensure mutual follow
        ensureFollow(resolvedUserId, finalRequesterId);
        ensureFollow(finalRequesterId, resolvedUserId);

        // 6. Synchronize accurate counters for both users
        userCounterSyncService.syncCounters(resolvedUserId);
        userCounterSyncService.syncCounters(finalRequesterId);

        // 7. Publish ACCEPT_FRIEND notification event to Kafka (UC-NO01)
        try {
            String acceptorName = "Người dùng";
            String acceptorAvatar = null;
            Optional<UserProfile> acceptorProfile = userProfileRepository.findByUserId(resolvedUserId);
            if (acceptorProfile.isEmpty()) {
                acceptorProfile = userProfileRepository.findById(resolvedUserId);
            }
            if (acceptorProfile.isPresent()) {
                UserProfile p = acceptorProfile.get();
                acceptorName = ((p.getLastName() != null ? p.getLastName() : "") + " " + (p.getFirstName() != null ? p.getFirstName() : "")).trim();
                if (acceptorName.isBlank()) acceptorName = "Một người dùng";
                acceptorAvatar = p.getAvatarUrl();
            }

            Map<String, Object> notifEvent = new HashMap<>();
            notifEvent.put("recipientId", finalRequesterId.toString());
            notifEvent.put("actorId", resolvedUserId.toString());
            notifEvent.put("type", "ACCEPT_FRIEND");
            notifEvent.put("title", "Chấp nhận kết bạn");
            notifEvent.put("content", acceptorName + " đã chấp nhận lời mời kết bạn của bạn.");
            notifEvent.put("targetId", resolvedUserId.toString());
            notifEvent.put("targetUrl", "/profile/" + resolvedUserId);
            notifEvent.put("avatarUrl", acceptorAvatar);

            kafkaTemplate.send("notification.in-app.send", notifEvent);
            log.info("Published ACCEPT_FRIEND notification event to Kafka for requester: {}", finalRequesterId);
        } catch (Exception e) {
            log.warn("Failed to publish ACCEPT_FRIEND notification event: {}", e.getMessage());
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

    private void ensureFollow(UUID followerId, UUID targetId) {
        userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(followerId, targetId, ConnectionType.FOLLOW)
                .ifPresentOrElse(
                        follow -> {
                            follow.setStatus(ConnectionStatus.ACCEPTED);
                            userConnectionRepository.save(follow);
                        },
                        () -> userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                                followerId, targetId, ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
                        ))
                );
    }
}
