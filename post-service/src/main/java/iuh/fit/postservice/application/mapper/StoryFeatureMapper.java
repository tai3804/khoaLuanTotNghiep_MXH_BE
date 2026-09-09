package iuh.fit.postservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.story.commands.create_story.CreateStoryCommand;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.StoryResult;
import iuh.fit.postservice.application.features.story.queries.get_story_viewers.StoryViewerResult;
import iuh.fit.postservice.domain.entities.Story;
import iuh.fit.postservice.domain.entities.StoryViewer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StoryFeatureMapper {

    Story toEntity(CreateStoryCommand command);

    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "userId", source = "entity.userId")
    @Mapping(target = "viewsCount", source = "viewsCount")
    @Mapping(target = "isViewedByMe", source = "isViewedByMe")
    StoryResult toResult(Story entity, long viewsCount, boolean isViewedByMe);

    StoryViewerResult toViewerResult(StoryViewer entity);

    default PagedResponse<StoryViewerResult> toPagedViewerResponse(Page<StoryViewer> page) {
        if (page == null) return null;
        List<StoryViewerResult> content = page.getContent() == null ? Collections.emptyList() :
                page.getContent().stream().map(this::toViewerResult).toList();
        return PagedResponse.<StoryViewerResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
