package iuh.fit.postservice.application.features.story.queries.get_active_stories;

import iuh.fit.postservice.application.mapper.StoryFeatureMapper;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryViewerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetActiveStoriesHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;
    StoryFeatureMapper storyFeatureMapper;

    @Transactional(readOnly = true)
    public List<UserStoriesResult> handle(GetActiveStoriesQuery query) {
        Instant now = Instant.now();
        List<UUID> followingIds = query.getFollowingIds() != null ? query.getFollowingIds() : List.of();

        List<Story> activeStories;
        if (!followingIds.isEmpty()) {
            activeStories = storyRepository.findActiveFeedStories(query.getCurrentUserId(), followingIds, now);
        } else {
            activeStories = storyRepository.findAllActiveStories(now);
        }

        if (activeStories.isEmpty()) {
            return Collections.emptyList();
        }

        // Group active stories by userId
        Map<UUID, List<Story>> userStoriesMap = activeStories.stream()
                .collect(Collectors.groupingBy(Story::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<UserStoriesResult> resultList = new ArrayList<>();

        for (Map.Entry<UUID, List<Story>> entry : userStoriesMap.entrySet()) {
            UUID authorId = entry.getKey();
            List<Story> stories = entry.getValue();

            boolean hasUnviewed = false;
            List<StoryResult> storyResults = new ArrayList<>();

            for (Story story : stories) {
                long viewsCount = storyViewerRepository.countByStoryId(story.getId());
                boolean isViewedByMe = query.getCurrentUserId() != null &&
                        storyViewerRepository.existsByStoryIdAndViewerId(story.getId(), query.getCurrentUserId());

                if (!isViewedByMe) {
                    hasUnviewed = true;
                }

                storyResults.add(storyFeatureMapper.toResult(story, viewsCount, isViewedByMe));
            }

            resultList.add(UserStoriesResult.builder()
                    .userId(authorId)
                    .hasUnviewedStories(hasUnviewed)
                    .stories(storyResults)
                    .build());
        }

        return resultList;
    }
}
