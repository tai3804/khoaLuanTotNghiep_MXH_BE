package iuh.fit.feedservice.application.features.feed.commands.process_post_deleted;

import iuh.fit.feedservice.infrastructure.persistence.repository.UserFeedItemRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessPostDeletedCommandHandler {

    UserFeedItemRepository userFeedItemRepository;

    @Transactional
    public void handle(ProcessPostDeletedCommand command) {
        if (command.getPostId() != null) {
            userFeedItemRepository.deleteByPostId(command.getPostId());
        }
    }
}
