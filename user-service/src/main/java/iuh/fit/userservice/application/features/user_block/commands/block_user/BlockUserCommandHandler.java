package iuh.fit.userservice.application.features.user_block.commands.block_user;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.UserBlock;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserBlockRepository;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BlockUserCommandHandler {

    UserBlockRepository userBlockRepository;
    UserConnectionRepository userConnectionRepository;
    UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(BlockUserCommand command) {
        UUID blockerId = command.getBlockerId();
        UUID targetId = command.getTargetId();

        if (blockerId.equals(targetId)) {
            throw new BusinessException(UserServiceErrorCode.CANNOT_CONNECT_SELF);
        }

        // Verify target user exists
        Optional<UserProfile> targetProfileOpt = userProfileRepository.findByUserId(targetId);
        if (targetProfileOpt.isEmpty()) {
            targetProfileOpt = userProfileRepository.findById(targetId);
            if (targetProfileOpt.isPresent()) {
                targetId = targetProfileOpt.get().getUserId();
            } else {
                throw new BusinessException(UserServiceErrorCode.USER_PROFILE_NOT_FOUND);
            }
        }

        UUID finalTargetId = targetId;

        // Check if already blocked
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, finalTargetId)) {
            return;
        }

        // Save block entity
        UserBlock block = UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(finalTargetId)
                .reason(command.getReason())
                .build();
        userBlockRepository.save(block);

        // Remove any existing friend/follow connections between the two users
        List<UserConnection> connections = userConnectionRepository.findAllConnectionsBetween(blockerId, finalTargetId);
        if (!connections.isEmpty()) {
            userConnectionRepository.deleteAll(connections);
        }
    }
}
