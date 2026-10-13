package iuh.fit.adminservice.application.features.moderation.commands.moderate_post;

import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.infrastructure.event.ModerationEventPublisher;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModeratePostCommandHandler {

    /** Chỉ các hành động tác động trực tiếp lên trạng thái bài viết mới hợp lệ ở endpoint này. */
    private static final Set<ModerationAction> SUPPORTED_ACTIONS =
            Set.of(ModerationAction.HIDE_POST, ModerationAction.DELETE_POST, ModerationAction.RESTORE_POST);

    ModerationLogRepository moderationLogRepository;
    ModerationEventPublisher moderationEventPublisher;

    @Transactional
    public void handle(ModeratePostCommand command) {
        if (!SUPPORTED_ACTIONS.contains(command.getAction())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ModerationLog log = ModerationLog.builder()
                .moderatorId(command.getModeratorId())
                .targetType(TargetType.POST)
                .targetId(command.getPostId())
                .action(command.getAction())
                .reason(command.getReason())
                .note(command.getNote())
                .build();

        moderationLogRepository.save(log);

        // Publish Kafka event to post-service
        moderationEventPublisher.publishPostModerated(
                command.getPostId(),
                command.getAction().name(),
                command.getReason(),
                command.getModeratorId()
        );
    }
}
