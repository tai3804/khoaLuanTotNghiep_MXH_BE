package iuh.fit.postservice.application.features.story.commands.delete_story;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
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
public class DeleteStoryHandler {

    StoryRepository storyRepository;

    @Transactional
    public void handle(DeleteStoryCommand command) {
        Story story = storyRepository.findByIdAndIsDeletedFalse(command.getStoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!story.getUserId().equals(command.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        story.setDeleted(true);
        storyRepository.save(story);
        log.info("Soft deleted story {} by user {}", story.getId(), command.getCurrentUserId());
    }
}
