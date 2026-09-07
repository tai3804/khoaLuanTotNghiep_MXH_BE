package iuh.fit.postservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionCommand;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionResult;
import iuh.fit.postservice.application.features.reaction.queries.get_post_reactions.GetPostReactionsResult;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.enums.ReactionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReactionFeatureMapper {

    Reaction toEntity(ToggleReactionCommand command);

    ToggleReactionResult toToggleResult(UUID postId, UUID userId, ReactionType currentReaction, long totalReactions);

    GetPostReactionsResult toGetReactionsResult(Reaction reaction);

    @Mapping(target = "content", source = "content")
    @Mapping(target = "page", source = "page.number")
    @Mapping(target = "size", source = "page.size")
    @Mapping(target = "totalElements", source = "page.totalElements")
    @Mapping(target = "totalPages", source = "page.totalPages")
    @Mapping(target = "last", source = "page.last")
    PagedResponse<GetPostReactionsResult> toPagedResponse(Page<Reaction> page, List<GetPostReactionsResult> content);
}
