package iuh.fit.postservice.application.features.story.commands.create_story;

import iuh.fit.postservice.application.features.story.queries.get_active_stories.StoryResult;
import iuh.fit.postservice.application.mapper.StoryFeatureMapper;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateStoryHandler {

    StoryRepository storyRepository;
    StoryFeatureMapper storyFeatureMapper;

    @Transactional
    public StoryResult handle(CreateStoryCommand command) {
        Story story = Story.builder()
                .userId(command.getUserId())
                .mediaUrl(command.getMediaUrl())
                .mediaType(command.getMediaType() != null ? command.getMediaType() : "IMAGE")
                .caption(command.getCaption())
                .privacy(command.getPrivacy() != null ? command.getPrivacy() : PostPrivacy.PUBLIC)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .isDeleted(false)
                .build();

        Story saved = storyRepository.save(story);
        log.info("Created new 24h story {} for user {}", saved.getId(), command.getUserId());
        return storyFeatureMapper.toResult(saved, 0, false);
    }
}
