package iuh.fit.userservice.application.features.user_connection.commands.send_friend_request;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
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

import iuh.fit.userservice.domain.entities.UserProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;
    KafkaTemplate<String, Object> kafkaTemplate;


    @Transactional
    public void handle(SendFriendRequestCommand command) {
        UUID targetUserId = command.getTargetId();
        Optional<UserProfile> targetProfileOpt = userProfileRepository.findByUserId(targetUserId);
        if (targetProfileOpt.isEmpty()) {
            targetProfileOpt = userProfileRepository.findById(targetUserId);
            if (targetProfileOpt.isPresent()) {
                targetUserId = targetProfileOpt.get().getUserId();
            } else {
                throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
            }
        }

        if (command.getRequesterId().equals(targetUserId)) {
            throw new BusinessException(UserServiceErrorCode.CANNOT_CONNECT_SELF);
        }

        // Rule 1: Sending friend request automatically FOLLOWS the target user
        UUID finalTargetUserId = targetUserId;
        userConnectionRepository.findByRequesterIdAndTargetIdAndType(command.getRequesterId(), finalTargetUserId, ConnectionType.FOLLOW)
                .ifPresentOrElse(
                        follow -> {
                            follow.setStatus(ConnectionStatus.ACCEPTED);
                            userConnectionRepository.save(follow);
                        },
                        () -> userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                                command.getRequesterId(), finalTargetUserId, ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
                        ))
                );

        // Check existing friend connection between A and B
        Optional<UserConnection> existingFriendship = userConnectionRepository.findConnectionBetween(
                command.getRequesterId(), finalTargetUserId, ConnectionType.FRIEND
        );

        if (existingFriendship.isPresent()) {
            UserConnection friendConn = existingFriendship.get();
            if (friendConn.getStatus() == ConnectionStatus.ACCEPTED) {
                throw new BusinessException(UserServiceErrorCode.CONNECTION_ALREADY_EXISTS);
            }
        }

        // Rule 2: Check if B is ALREADY following A -> Mutual follow means they automatically become FRIENDS!
        boolean targetFollowsRequester = userConnectionRepository.existsByRequesterIdAndTargetIdAndTypeAndStatus(
                finalTargetUserId, command.getRequesterId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
        );

        ConnectionStatus initialStatus = targetFollowsRequester ? ConnectionStatus.ACCEPTED : ConnectionStatus.PENDING;

        if (existingFriendship.isPresent()) {
            UserConnection conn = existingFriendship.get();
            conn.setRequesterId(command.getRequesterId());
            conn.setTargetId(finalTargetUserId);
            conn.setStatus(initialStatus);
            userConnectionRepository.save(conn);
        } else {
            userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                    command.getRequesterId(), finalTargetUserId, ConnectionType.FRIEND, initialStatus
            ));
        }

        // Publish in-app notification event to Kafka (UC-NO01)
        try {
            String requesterName = "Người dùng";
            String requesterAvatar = null;
            Optional<UserProfile> requesterProfile = userProfileRepository.findByUserId(command.getRequesterId());
            if (requesterProfile.isPresent()) {
                UserProfile p = requesterProfile.get();
                requesterName = ((p.getLastName() != null ? p.getLastName() : "") + " " + (p.getFirstName() != null ? p.getFirstName() : "")).trim();
                if (requesterName.isBlank()) requesterName = "Một người dùng";
                requesterAvatar = p.getAvatarUrl();
            }

            Map<String, Object> notifEvent = new HashMap<>();
            notifEvent.put("recipientId", finalTargetUserId.toString());
            notifEvent.put("actorId", command.getRequesterId().toString());
            notifEvent.put("type", "FRIEND_REQUEST");
            notifEvent.put("title", "Lời mời kết bạn");
            notifEvent.put("content", requesterName + " đã gửi cho bạn một lời mời kết bạn.");
            notifEvent.put("targetId", command.getRequesterId().toString());
            notifEvent.put("targetUrl", "/profile/" + command.getRequesterId());
            notifEvent.put("avatarUrl", requesterAvatar);

            kafkaTemplate.send("notification.in-app.send", notifEvent);
            log.info("Published FRIEND_REQUEST notification event to Kafka for target: {}", finalTargetUserId);
        } catch (Exception e) {
            log.warn("Failed to publish FRIEND_REQUEST notification event: {}", e.getMessage());
        }
    }
}

