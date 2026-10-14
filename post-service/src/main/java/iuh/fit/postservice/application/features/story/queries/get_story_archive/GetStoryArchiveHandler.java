package iuh.fit.postservice.application.features.story.queries.get_story_archive;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.StoryResult;
import iuh.fit.postservice.application.mapper.StoryFeatureMapper;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.StoryViewerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetStoryArchiveHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;
    StoryFeatureMapper storyFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<StoryResult> handle(GetStoryArchiveQuery query) {
        int page = query.getFilter() != null ? Math.max(0, query.getFilter().getPage() - 1) : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;
        Pageable pageable = PageRequest.of(page, size);

        Instant now = Instant.now();
        Page<Story> storyPage = storyRepository.findArchivedStoriesByUserId(query.getCurrentUserId(), now, pageable);

        List<StoryResult> content = storyPage.getContent().isEmpty() ? Collections.emptyList() :
                storyPage.getContent().stream().map(story -> {
                    long viewsCount = storyViewerRepository.countByStoryId(story.getId());
                    return storyFeatureMapper.toResult(story, viewsCount, true);
                }).toList();

        return PagedResponse.<StoryResult>builder()
                .content(content)
                .page(storyPage.getNumber() + 1)
                .size(storyPage.getSize())
                .totalElements(storyPage.getTotalElements())
                .totalPages(storyPage.getTotalPages())
                .last(storyPage.isLast())
                .build();
    }
}
