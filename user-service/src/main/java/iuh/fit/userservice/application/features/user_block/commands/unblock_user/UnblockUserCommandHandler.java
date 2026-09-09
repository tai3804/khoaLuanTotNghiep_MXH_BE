package iuh.fit.userservice.application.features.user_block.commands.unblock_user;

import iuh.fit.userservice.domain.repository.UserBlockRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnblockUserCommandHandler {

    UserBlockRepository userBlockRepository;

    @Transactional
    public void handle(UnblockUserCommand command) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(command.getBlockerId(), command.getTargetId());
    }
}
