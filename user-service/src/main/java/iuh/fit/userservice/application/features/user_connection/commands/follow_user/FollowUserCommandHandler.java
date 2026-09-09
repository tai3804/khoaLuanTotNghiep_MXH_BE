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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.userservice.domain.entities.UserProfile;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FollowUserCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

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
        userConnectionRepository.findByRequesterIdAndTargetIdAndType(command.getFollowerId(), finalTargetUserId, ConnectionType.FOLLOW)
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
    }
}
