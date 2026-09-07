package iuh.fit.feedservice.application.mapper;

import iuh.fit.feedservice.application.features.feed.queries.get_user_feed.GetUserFeedResult;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeedFeatureMapper {

    @Mapping(target = "feedItemId", source = "id")
    GetUserFeedResult toResult(UserFeedItem item);
}
