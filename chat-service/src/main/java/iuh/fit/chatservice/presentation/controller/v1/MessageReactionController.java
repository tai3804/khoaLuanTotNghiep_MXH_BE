package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.message.commands.toggle_reaction.ToggleMessageReactionCommand;
import iuh.fit.chatservice.application.features.message.commands.toggle_reaction.ToggleMessageReactionCommandHandler;
import iuh.fit.chatservice.application.features.message.queries.get_message_reactions.GetMessageReactionsQuery;
import iuh.fit.chatservice.application.features.message.queries.get_message_reactions.GetMessageReactionsQueryHandler;
import iuh.fit.chatservice.domain.entities.MessageReaction;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.request.ToggleReactionRequest;
import iuh.fit.chatservice.presentation.dto.response.MessageReactionResponse;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/conversations/{conversationId}/messages/{messageId}/reactions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Message Reactions", description = "APIs for toggling and viewing emoji reactions on chat messages")
@SecurityRequirement(name = "bearerAuth")
public class MessageReactionController {

    ToggleMessageReactionCommandHandler toggleMessageReactionCommandHandler;
    GetMessageReactionsQueryHandler getMessageReactionsQueryHandler;
    SimpMessagingTemplate messagingTemplate;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Toggle emoji reaction", description = "Toggles (add/change/remove) an emoji reaction on a message")
    public ResponseEntity<ApiResponse<MessageReactionResponse>> toggleReaction(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ToggleReactionRequest request) {
        UUID currentUserId = getCurrentUserId();

        ToggleMessageReactionCommand command = ToggleMessageReactionCommand.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .userId(currentUserId)
                .emoji(request.getEmoji())
                .build();

        Optional<MessageReaction> reactionOpt = toggleMessageReactionCommandHandler.handle(command);

        MessageReactionResponse response = reactionOpt.map(r -> MessageReactionResponse.builder()
                .id(r.getId())
                .messageId(r.getMessageId())
                .conversationId(r.getConversationId())
                .userId(r.getUserId())
                .emoji(r.getEmoji())
                .build()).orElse(null);

        // Real-time broadcast
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/reactions",
                (Object) java.util.Map.of(
                        "messageId", messageId,
                        "userId", currentUserId,
                        "emoji", request.getEmoji(),
                        "action", reactionOpt.isPresent() ? "TOGGLED" : "REMOVED"
                ));

        return ResponseEntity.ok(ApiResponse.success(response, reactionOpt.isPresent() ? "Reaction updated" : "Reaction removed"));
    }

    @GetMapping
    @Operation(summary = "Get message reactions", description = "Retrieves all reactions added to a specific message")
    public ResponseEntity<ApiResponse<List<MessageReactionResponse>>> getReactions(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        UUID currentUserId = getCurrentUserId();

        GetMessageReactionsQuery query = GetMessageReactionsQuery.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .userId(currentUserId)
                .build();

        List<MessageReaction> reactions = getMessageReactionsQueryHandler.handle(query);
        List<MessageReactionResponse> responseList = reactions.stream().map(r -> MessageReactionResponse.builder()
                .id(r.getId())
                .messageId(r.getMessageId())
                .conversationId(r.getConversationId())
                .userId(r.getUserId())
                .emoji(r.getEmoji())
                .build()).toList();

        return ResponseEntity.ok(ApiResponse.success(responseList, "Reactions retrieved successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
