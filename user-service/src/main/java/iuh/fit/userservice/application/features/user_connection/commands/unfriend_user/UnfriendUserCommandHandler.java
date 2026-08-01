package iuh.fit.userservice.application.features.user_connection.commands.unfriend_user;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnfriendUserCommandHandler {

    UserConnectionRepository userConnectionRepository;

    @Transactional
    public void handle(UnfriendUserCommand command) {
        userConnectionRepository.findAcceptedFriendship(command.getUserId(), command.getFriendId())
                .ifPresentOrElse(
                        userConnectionRepository::delete,
                        () -> {
                            throw new BusinessException(UserServiceErrorCode.CONNECTION_NOT_FOUND);
                        }
                );
    }
}
