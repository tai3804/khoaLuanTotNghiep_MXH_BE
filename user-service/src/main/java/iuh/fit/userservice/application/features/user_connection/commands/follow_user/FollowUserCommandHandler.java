package iuh.fit.userservice.application.features.user_connection.commands.follow_user;

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
public class FollowUserCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void handle(FollowUserCommand command) {
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

        if (command.getFollowerId().equals(targetUserId)) {
            throw new BusinessException(UserServiceErrorCode.CANNOT_CONNECT_SELF);
        }

        // Save or update FOLLOW connection from A to B
        UUID finalTargetUserId = targetUserId;
        userConnectionRepository.findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(command.getFollowerId(), finalTargetUserId, ConnectionType.FOLLOW)
                .ifPresentOrElse(
                        follow -> {
                            follow.setStatus(ConnectionStatus.ACCEPTED);
                            userConnectionRepository.save(follow);
                        },
                        () -> userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                                command.getFollowerId(), finalTargetUserId, ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
                        ))
                );

        // Rule: Check if target user B is ALREADY following A -> Mutual follow means they automatically become FRIENDS!
        boolean targetFollowsFollower = userConnectionRepository.existsByRequesterIdAndTargetIdAndTypeAndStatus(
                finalTargetUserId, command.getFollowerId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
        );

        if (targetFollowsFollower) {
            Optional<UserConnection> existingFriendship = userConnectionRepository.findConnectionBetween(
                    command.getFollowerId(), finalTargetUserId, ConnectionType.FRIEND
            );

            if (existingFriendship.isPresent()) {
                UserConnection conn = existingFriendship.get();
                conn.setStatus(ConnectionStatus.ACCEPTED);
                userConnectionRepository.save(conn);
            } else {
                userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                        command.getFollowerId(), finalTargetUserId, ConnectionType.FRIEND, ConnectionStatus.ACCEPTED
                ));
            }
        }

        // Publish FOLLOW_USER notification event to Kafka (UC-NO01)
        try {
            String followerName = "Người dùng";
            String followerAvatar = null;
            Optional<UserProfile> followerProfile = userProfileRepository.findByUserId(command.getFollowerId());
            if (followerProfile.isPresent()) {
                UserProfile p = followerProfile.get();
                followerName = ((p.getLastName() != null ? p.getLastName() : "") + " " + (p.getFirstName() != null ? p.getFirstName() : "")).trim();
                if (followerName.isBlank()) followerName = "Một người dùng";
                followerAvatar = p.getAvatarUrl();
            }

            Map<String, Object> notifEvent = new HashMap<>();
            notifEvent.put("recipientId", finalTargetUserId.toString());
            notifEvent.put("actorId", command.getFollowerId().toString());
            notifEvent.put("type", "FOLLOW_USER");
            notifEvent.put("title", "Người theo dõi mới");
            notifEvent.put("content", followerName + " đã bắt đầu theo dõi bạn.");
            notifEvent.put("targetId", command.getFollowerId().toString());
            notifEvent.put("targetUrl", "/profile/" + command.getFollowerId());
            notifEvent.put("avatarUrl", followerAvatar);

            kafkaTemplate.send("notification.in-app.send", notifEvent);
            log.info("Published FOLLOW_USER notification event to Kafka for target: {}", finalTargetUserId);
        } catch (Exception e) {
            log.warn("Failed to publish FOLLOW_USER notification event: {}", e.getMessage());
        }
    }
}
