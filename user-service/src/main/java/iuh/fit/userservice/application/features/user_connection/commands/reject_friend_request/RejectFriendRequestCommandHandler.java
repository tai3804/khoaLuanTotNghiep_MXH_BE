package iuh.fit.userservice.application.features.user_connection.commands.reject_friend_request;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RejectFriendRequestCommandHandler {

    UserConnectionRepository userConnectionRepository;

    @Transactional
    public void handle(RejectFriendRequestCommand command) {
        UserConnection friendConn = userConnectionRepository.findConnectionBetween(
                command.getRequesterId(), command.getUserId(), ConnectionType.FRIEND
        ).orElseThrow(() -> new BusinessException(UserServiceErrorCode.CONNECTION_NOT_FOUND));

        friendConn.setStatus(ConnectionStatus.REJECTED);
        userConnectionRepository.save(friendConn);
    }
}
