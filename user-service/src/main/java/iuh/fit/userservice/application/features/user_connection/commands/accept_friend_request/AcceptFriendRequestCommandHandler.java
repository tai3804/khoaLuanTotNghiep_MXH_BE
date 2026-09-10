package iuh.fit.userservice.application.features.user_connection.commands.accept_friend_request;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.entities.UserProfile;
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
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void handle(AcceptFriendRequestCommand command) {
        UserConnection friendConn = userConnectionRepository.findConnectionBetween(
                command.getRequesterId(), command.getUserId(), ConnectionType.FRIEND
        ).or(() -> userConnectionRepository.findById(command.getRequesterId()))
        .orElseThrow(() -> new BusinessException(UserServiceErrorCode.CONNECTION_NOT_FOUND));

        friendConn.setStatus(ConnectionStatus.ACCEPTED);
        userConnectionRepository.save(friendConn);

        UUID requesterId = friendConn.getRequesterId().equals(command.getUserId())
                ? friendConn.getTargetId()
                : friendConn.getRequesterId();

        // Rule: Accepting friend request means User B also FOLLOWS User A
        ensureFollow(command.getUserId(), requesterId);
        // Ensure User A also FOLLOWS User B
        ensureFollow(requesterId, command.getUserId());

        // Publish ACCEPT_FRIEND notification event to Kafka (UC-NO01)
        try {
            String acceptorName = "Người dùng";
            String acceptorAvatar = null;
            Optional<UserProfile> acceptorProfile = userProfileRepository.findByUserId(command.getUserId());
            if (acceptorProfile.isPresent()) {
                UserProfile p = acceptorProfile.get();
                acceptorName = ((p.getLastName() != null ? p.getLastName() : "") + " " + (p.getFirstName() != null ? p.getFirstName() : "")).trim();
                if (acceptorName.isBlank()) acceptorName = "Một người dùng";
                acceptorAvatar = p.getAvatarUrl();
            }

            Map<String, Object> notifEvent = new HashMap<>();
            notifEvent.put("recipientId", requesterId.toString());
            notifEvent.put("actorId", command.getUserId().toString());
            notifEvent.put("type", "ACCEPT_FRIEND");
            notifEvent.put("title", "Chấp nhận kết bạn");
            notifEvent.put("content", acceptorName + " đã chấp nhận lời mời kết bạn của bạn.");
            notifEvent.put("targetId", command.getUserId().toString());
            notifEvent.put("targetUrl", "/profile/" + command.getUserId());
            notifEvent.put("avatarUrl", acceptorAvatar);

            kafkaTemplate.send("notification.in-app.send", notifEvent);
            log.info("Published ACCEPT_FRIEND notification event to Kafka for requester: {}", requesterId);
        } catch (Exception e) {
            log.warn("Failed to publish ACCEPT_FRIEND notification event: {}", e.getMessage());
        }
    }


    private void ensureFollow(UUID followerId, UUID targetId) {
        userConnectionRepository.findByRequesterIdAndTargetIdAndType(followerId, targetId, ConnectionType.FOLLOW)
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
