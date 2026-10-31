package iuh.fit.postservice.application.features.story.queries.get_active_stories;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.application.mapper.StoryFeatureMapper;
import iuh.fit.postservice.domain.entities.Story;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetActiveStoriesHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;
    StoryFeatureMapper storyFeatureMapper;
    UserConnectionClient userConnectionClient;

    @Transactional(readOnly = true)
    public List<UserStoriesResult> handle(GetActiveStoriesQuery query) {
        UUID currentUserId = query.getCurrentUserId();
        if (currentUserId == null) {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        Set<UUID> friendIds = new HashSet<>();
        Set<UUID> followingIds = new HashSet<>();
        if (query.getFollowingIds() != null) {
            followingIds.addAll(query.getFollowingIds());
        }

        try {
            ApiResponse<Map<String, List<UUID>>> graphResponse = userConnectionClient.getSocialGraph();
            Map<String, List<UUID>> graph = graphResponse != null ? graphResponse.getData() : null;
            if (graph != null) {
                List<UUID> fIds = graph.get("friendIds");
                if (fIds != null) {
                    friendIds.addAll(fIds);
                }
                List<UUID> folIds = graph.get("followingIds");
                if (folIds != null) {
                    followingIds.addAll(folIds);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load social graph for stories feed of user {}: {}", currentUserId, e.getMessage());
        }

        Set<UUID> candidateAuthorIds = new LinkedHashSet<>();
        candidateAuthorIds.addAll(friendIds);
        candidateAuthorIds.addAll(followingIds);

        List<Story> rawStories;
        if (!candidateAuthorIds.isEmpty()) {
            rawStories = storyRepository.findActiveFeedStories(currentUserId, new ArrayList<>(candidateAuthorIds), now);
        } else {
            rawStories = storyRepository.findByUserIdAndExpiresAtAfterAndIsDeletedFalseAndIsArchivedFalseOrderByCreatedAtDesc(
                    currentUserId, now
            );
        }

        if (rawStories.isEmpty()) {
            return Collections.emptyList();
        }

        List<Story> visibleStories = rawStories.stream()
                .filter(story -> canViewStory(story, currentUserId, friendIds, followingIds))
                .toList();

        if (visibleStories.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, List<Story>> userStoriesMap = visibleStories.stream()
                .collect(Collectors.groupingBy(Story::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<UserStoriesResult> resultList = new ArrayList<>();

        for (Map.Entry<UUID, List<Story>> entry : userStoriesMap.entrySet()) {
            UUID authorId = entry.getKey();
            List<Story> stories = entry.getValue();

            boolean hasUnviewed = false;
            List<StoryResult> storyResults = new ArrayList<>();

            for (Story story : stories) {
                long viewsCount = storyViewerRepository.countByStoryId(story.getId());
                boolean isViewedByMe = storyViewerRepository.existsByStoryIdAndViewerId(story.getId(), currentUserId);

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

    private boolean canViewStory(Story story, UUID viewerId, Set<UUID> friendIds, Set<UUID> followingIds) {
        if (story == null || viewerId == null) {
            return false;
        }
        if (viewerId.equals(story.getUserId())) {
            return true;
        }
        PostPrivacy privacy = story.getPrivacy() != null ? story.getPrivacy() : PostPrivacy.PUBLIC;
        return switch (privacy) {
            case PRIVATE, CUSTOM -> false;
            case FRIENDS -> friendIds.contains(story.getUserId());
            case PUBLIC -> friendIds.contains(story.getUserId()) || followingIds.contains(story.getUserId());
        };
    }
}
