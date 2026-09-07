package iuh.fit.feedservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedQuery;
import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedResult;
import iuh.fit.feedservice.presentation.dto.response.FeedItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeedPresentationMapper {

    GetUserFeedQuery toQuery(UUID userId, int page, int size);

    FeedItemResponse toResponse(GetUserFeedResult result);

    List<FeedItemResponse> toResponseList(List<GetUserFeedResult> results);

    PagedResponse<FeedItemResponse> toPagedResponse(PagedResponse<GetUserFeedResult> pagedResult);
}
