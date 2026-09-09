package iuh.fit.postservice.application.features.story.commands.view_story;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.domain.entities.StoryViewer;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryViewerRepository;
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
public class ViewStoryHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;

    @Transactional
    public void handle(ViewStoryCommand command) {
        Story story = storyRepository.findByIdAndIsDeletedFalse(command.getStoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!storyViewerRepository.existsByStoryIdAndViewerId(story.getId(), command.getViewerId())) {
            StoryViewer viewer = StoryViewer.builder()
                    .storyId(story.getId())
                    .viewerId(command.getViewerId())
                    .build();
            storyViewerRepository.save(viewer);
            log.info("Recorded view for story {} by user {}", story.getId(), command.getViewerId());
        }
    }
}
