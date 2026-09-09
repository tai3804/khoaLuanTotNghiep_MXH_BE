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

    default PagedResponse<ReactionResponse> toPagedResponse(PagedResponse<GetPostReactionsResult> result) {
        if (result == null) return null;
        java.util.List<ReactionResponse> list = result.getContent() == null ? java.util.Collections.emptyList() :
                result.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<ReactionResponse>builder()
                .content(list)
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }
}
