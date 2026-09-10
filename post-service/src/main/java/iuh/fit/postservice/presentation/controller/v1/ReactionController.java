package iuh.fit.postservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionCommand;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionCommandHandler;
import iuh.fit.postservice.application.features.reaction.commands.toggle_reaction.ToggleReactionResult;
import iuh.fit.postservice.application.features.reaction.queries.get_post_reactions.GetPostReactionsQuery;
import iuh.fit.postservice.application.features.reaction.queries.get_post_reactions.GetPostReactionsQueryHandler;
import iuh.fit.postservice.application.features.reaction.queries.get_post_reactions.GetPostReactionsResult;
import iuh.fit.postservice.domain.enums.ReactionType;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.constants.MessageConstants;
import iuh.fit.postservice.presentation.dto.request.ToggleReactionRequest;
import iuh.fit.postservice.presentation.dto.response.ReactionResponse;
import iuh.fit.postservice.presentation.mapper.ReactionPresentationMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.POST_API + "/{postId}/reactions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Post Reactions", description = "APIs for toggling and viewing reactions on posts")
@SecurityRequirement(name = "bearerAuth")
public class ReactionController {

    ToggleReactionCommandHandler toggleReactionCommandHandler;
    GetPostReactionsQueryHandler getPostReactionsQueryHandler;
    ReactionPresentationMapper reactionPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Toggle reaction on post", description = "Toggles reaction (LIKE, LOVE, HAHA, WOW, SAD, ANGRY) on a post. Toggling same reaction will remove it.")
    public ResponseEntity<ApiResponse<ToggleReactionResult>> toggleReaction(
            @PathVariable UUID postId,
            @RequestBody(required = false) ToggleReactionRequest request) {
        UUID currentUserId = getCurrentUserId();
        ToggleReactionRequest safeRequest = (request != null && request.getType() != null)
                ? request
                : ToggleReactionRequest.builder().type(ReactionType.LIKE).build();

        ToggleReactionCommand command = reactionPresentationMapper.toToggleCommand(safeRequest, postId, currentUserId);
        ToggleReactionResult result = toggleReactionCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(result, MessageConstants.REACTION_TOGGLED_SUCCESSFULLY));
    }

    @GetMapping
    @Operation(summary = "Get post reactions", description = "Retrieves paginated list of reactions for a post")
    public ResponseEntity<ApiResponse<List<ReactionResponse>>> getPostReactions(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        GetPostReactionsQuery query = GetPostReactionsQuery.builder()
                .postId(postId)
                .page(page)
                .size(size)
                .build();
        PagedResponse<GetPostReactionsResult> result = getPostReactionsQueryHandler.handle(query);
        PagedResponse<ReactionResponse> pagedResponse = reactionPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.REACTIONS_RETRIEVED_SUCCESSFULLY));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}