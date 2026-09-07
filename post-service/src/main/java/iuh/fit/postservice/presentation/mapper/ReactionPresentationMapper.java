package iuh.fit.postservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionCommand;
import iuh.fit.postservice.application.features.reaction.queries.get_post_reactions.GetPostReactionsResult;
import iuh.fit.postservice.presentation.dto.request.ToggleReactionRequest;
import iuh.fit.postservice.presentation.dto.response.ReactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReactionPresentationMapper {

    @Mapping(target = "postId", source = "postId")
    @Mapping(target = "userId", source = "userId")
    ToggleReactionCommand toToggleCommand(ToggleReactionRequest request, UUID postId, UUID userId);

    ReactionResponse toResponse(GetPostReactionsResult result);

    PagedResponse<ReactionResponse> toPagedResponse(PagedResponse<GetPostReactionsResult> result);
}
