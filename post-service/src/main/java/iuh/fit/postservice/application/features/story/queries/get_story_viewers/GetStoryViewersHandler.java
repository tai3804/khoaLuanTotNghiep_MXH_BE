package iuh.fit.postservice.application.features.story.queries.get_story_viewers;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.postservice.application.mapper.StoryFeatureMapper;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.domain.entities.StoryViewer;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetStoryViewersHandler {

    StoryRepository storyRepository;
    StoryViewerRepository storyViewerRepository;
    StoryFeatureMapper storyFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<StoryViewerResult> handle(GetStoryViewersQuery query) {
        Story story = storyRepository.findByIdAndIsDeletedFalse(query.getStoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!story.getUserId().equals(query.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        int page = query.getFilter() != null ? query.getFilter().getPage() : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;
        Pageable pageable = PageRequest.of(page, size);

        Page<StoryViewer> viewerPage = storyViewerRepository.findByStoryIdOrderByViewedAtDesc(query.getStoryId(), pageable);
        return storyFeatureMapper.toPagedViewerResponse(viewerPage);
    }
}
