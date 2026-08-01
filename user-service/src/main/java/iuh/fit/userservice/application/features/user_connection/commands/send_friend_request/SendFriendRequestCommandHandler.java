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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional
    public void handle(SendFriendRequestCommand command) {
        if (command.getRequesterId().equals(command.getTargetId())) {
            throw new BusinessException(UserServiceErrorCode.CANNOT_CONNECT_SELF);
        }

        if (!userProfileRepository.existsById(command.getTargetId())) {
            throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
        }

        // Rule 1: Sending friend request automatically FOLLOWS the target user
        userConnectionRepository.findByRequesterIdAndTargetIdAndType(command.getRequesterId(), command.getTargetId(), ConnectionType.FOLLOW)
                .ifPresentOrElse(
                        follow -> {
                            follow.setStatus(ConnectionStatus.ACCEPTED);
                            userConnectionRepository.save(follow);
                        },
                        () -> userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                                command.getRequesterId(), command.getTargetId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
                        ))
                );

        // Check existing friend connection between A and B
        Optional<UserConnection> existingFriendship = userConnectionRepository.findConnectionBetween(
                command.getRequesterId(), command.getTargetId(), ConnectionType.FRIEND
        );

        if (existingFriendship.isPresent()) {
            UserConnection friendConn = existingFriendship.get();
            if (friendConn.getStatus() == ConnectionStatus.ACCEPTED) {
                throw new BusinessException(UserServiceErrorCode.CONNECTION_ALREADY_EXISTS);
            }
        }

        // Rule 2: Check if B is ALREADY following A -> Mutual follow means they automatically become FRIENDS!
        boolean targetFollowsRequester = userConnectionRepository.existsByRequesterIdAndTargetIdAndTypeAndStatus(
                command.getTargetId(), command.getRequesterId(), ConnectionType.FOLLOW, ConnectionStatus.ACCEPTED
        );

        ConnectionStatus initialStatus = targetFollowsRequester ? ConnectionStatus.ACCEPTED : ConnectionStatus.PENDING;

        if (existingFriendship.isPresent()) {
            UserConnection conn = existingFriendship.get();
            conn.setRequesterId(command.getRequesterId());
            conn.setTargetId(command.getTargetId());
            conn.setStatus(initialStatus);
            userConnectionRepository.save(conn);
        } else {
            userConnectionRepository.save(userConnectionApplicationMapper.toEntity(
                    command.getRequesterId(), command.getTargetId(), ConnectionType.FRIEND, initialStatus
            ));
        }
    }
}
