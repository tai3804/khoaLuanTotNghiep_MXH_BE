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

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FollowUserCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional
    public void handle(FollowUserCommand command) {
        if (command.getFollowerId().equals(command.getTargetId())) {
            throw new BusinessException(UserServiceErrorCode.CANNOT_CONNECT_SELF);
        }

        if (!userProfileRepository.existsById(command.getTargetId())) {
            throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
        }

        // Save or update FOLLOW connection from A to B
        userConnectionRepository.findByRequesterIdAndTargetIdAndType(command.getFollowerId(), command.getTargetId(), ConnectionType.FOLLOW)
                .ifPresentOrElse(
                        follow -> {
                            follow.setStatus(ConnectionStatus.ACCEPTED);
                            userConnectionRepository.save(follow);
                        },
                        () -> userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                                command.getFollowerId(), command.getTargetId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
                        ))
                );

        // Rule: Check if target user B is ALREADY following A -> Mutual follow means they automatically become FRIENDS!
        boolean targetFollowsFollower = userConnectionRepository.existsByRequesterIdAndTargetIdAndTypeAndStatus(
                command.getTargetId(), command.getFollowerId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
        );

        if (targetFollowsFollower) {
            Optional<UserConnection> existingFriendship = userConnectionRepository.findConnectionBetween(
                    command.getFollowerId(), command.getTargetId(), ConnectionType.FRIEND
            );

            if (existingFriendship.isPresent()) {
                UserConnection conn = existingFriendship.get();
                conn.setStatus(ConnectionStatus.ACCEPTED);
                userConnectionRepository.save(conn);
            } else {
                userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                        command.getFollowerId(), command.getTargetId(), ConnectionType.FRIEND, ConnectionStatus.ACCEPTED
                ));
            }
        }
    }
}
