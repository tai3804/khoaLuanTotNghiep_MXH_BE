package iuh.fit.adminservice.application.features.moderation.commands.moderate_post;

import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.infrastructure.event.ModerationEventPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModeratePostCommandHandler {

    ModerationLogRepository moderationLogRepository;
    ModerationEventPublisher moderationEventPublisher;

    @Transactional
    public void handle(ModeratePostCommand command) {
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
