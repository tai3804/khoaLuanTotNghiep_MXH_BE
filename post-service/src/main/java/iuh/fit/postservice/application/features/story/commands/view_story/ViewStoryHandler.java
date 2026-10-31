package iuh.fit.postservice.application.features.story.commands.view_story;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.domain.entities.StoryViewer;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryViewerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViewStoryHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;
    UserConnectionClient userConnectionClient;

    @Transactional
    public long handle(ViewStoryCommand command) {
        Story story = storyRepository.findByIdAndIsDeletedFalse(command.getStoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!canViewStory(story, command.getViewerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (story.isArchived() || (story.getExpiresAt() != null && story.getExpiresAt().isBefore(java.time.Instant.now()))) {
            return storyViewerRepository.countByStoryId(story.getId());
        }

        if (!storyViewerRepository.existsByStoryIdAndViewerId(story.getId(), command.getViewerId())) {
            StoryViewer viewer = StoryViewer.builder()
                    .storyId(story.getId())
                    .viewerId(command.getViewerId())
                    .build();
            storyViewerRepository.saveAndFlush(viewer);
            log.info("Recorded view for story {} by user {}", story.getId(), command.getViewerId());
        }

        return storyViewerRepository.countByStoryId(story.getId());
    }

    private boolean canViewStory(Story story, UUID viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(story.getUserId())) {
            return true;
        }
        PostPrivacy privacy = story.getPrivacy() != null ? story.getPrivacy() : PostPrivacy.PUBLIC;
        if (privacy == PostPrivacy.PRIVATE || privacy == PostPrivacy.CUSTOM) {
            return false;
        }
        if (privacy == PostPrivacy.FRIENDS) {
            try {
                ApiResponse<Map<String, Object>> response = userConnectionClient.getConnectionStatus(story.getUserId());
                Map<String, Object> data = response != null ? response.getData() : null;
                Object value = data != null ? data.get("friend") : null;
                if (value == null && data != null) {
                    value = data.get("isFriend");
                }
                return value instanceof Boolean bool && bool;
            } catch (Exception e) {
                log.warn("Story friend check failed between viewer {} and author {}: {}", viewerId, story.getUserId(), e.getMessage());
                return false;
            }
        }
        return true;
    }
}
