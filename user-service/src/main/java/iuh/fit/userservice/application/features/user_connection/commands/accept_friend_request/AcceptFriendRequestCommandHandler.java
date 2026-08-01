package iuh.fit.userservice.application.features.user_connection.commands.accept_friend_request;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AcceptFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    @Transactional
    public void handle(AcceptFriendRequestCommand command) {
        UserConnection friendConn = userConnectionRepository.findConnectionBetween(
                command.getRequesterId(), command.getUserId(), ConnectionType.FRIEND
        ).orElseThrow(() -> new BusinessException(UserServiceErrorCode.CONNECTION_NOT_FOUND));

        friendConn.setStatus(ConnectionStatus.ACCEPTED);
        userConnectionRepository.save(friendConn);

        // Rule: Accepting friend request means User B also FOLLOWS User A
        ensureFollow(command.getUserId(), command.getRequesterId());
        // Ensure User A also FOLLOWS User B
        ensureFollow(command.getRequesterId(), command.getUserId());
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
