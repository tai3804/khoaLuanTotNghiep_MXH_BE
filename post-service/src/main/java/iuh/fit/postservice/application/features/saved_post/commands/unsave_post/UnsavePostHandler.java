package iuh.fit.postservice.application.features.saved_post.commands.unsave_post;

import iuh.fit.postservice.infrastructure.persistence.repository.SavedPostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnsavePostHandler {

    SavedPostRepository savedPostRepository;

    @Transactional
    public void handle(UnsavePostCommand command) {
        savedPostRepository.deleteByUserIdAndPostId(command.getUserId(), command.getPostId());
        log.info("Unsaved post {} for user {}", command.getPostId(), command.getUserId());
    }
}
