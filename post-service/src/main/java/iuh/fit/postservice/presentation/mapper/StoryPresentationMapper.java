package iuh.fit.postservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.story.commands.create_story.CreateStoryCommand;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.StoryResult;
import iuh.fit.postservice.application.features.story.queries.get_active_stories.UserStoriesResult;
import iuh.fit.postservice.application.features.story.queries.get_story_viewers.StoryViewerResult;
import iuh.fit.postservice.presentation.dto.request.CreateStoryRequest;
import iuh.fit.postservice.presentation.dto.response.StoryResponse;
import iuh.fit.postservice.presentation.dto.response.StoryViewerResponse;
import iuh.fit.postservice.presentation.dto.response.UserStoriesResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StoryPresentationMapper {

    @Mapping(target = "userId", source = "userId")
    CreateStoryCommand toCommand(CreateStoryRequest request, UUID userId);

    StoryResponse toResponse(StoryResult result);

    UserStoriesResponse toResponse(UserStoriesResult result);

    List<UserStoriesResponse> toUserStoriesResponseList(List<UserStoriesResult> results);

    StoryViewerResponse toResponse(StoryViewerResult result);

    default PagedResponse<StoryViewerResponse> toPagedViewerResponse(PagedResponse<StoryViewerResult> pagedResult) {
        if (pagedResult == null) return null;
        List<StoryViewerResponse> content = pagedResult.getContent() == null ? Collections.emptyList() :
                pagedResult.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<StoryViewerResponse>builder()
                .content(content)
                .page(pagedResult.getPage())
                .size(pagedResult.getSize())
                .totalElements(pagedResult.getTotalElements())
                .totalPages(pagedResult.getTotalPages())
                .last(pagedResult.isLast())
                .build();
    }
}
