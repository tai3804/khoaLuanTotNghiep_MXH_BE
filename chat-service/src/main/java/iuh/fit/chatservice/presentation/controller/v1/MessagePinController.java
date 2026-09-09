package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.message.commands.pin_message.PinMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.pin_message.PinMessageCommandHandler;
import iuh.fit.chatservice.application.features.message.commands.unpin_message.UnpinMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.unpin_message.UnpinMessageCommandHandler;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import iuh.fit.chatservice.application.features.message.queries.get_pinned_messages.GetPinnedMessagesQuery;
import iuh.fit.chatservice.application.features.message.queries.get_pinned_messages.GetPinnedMessagesQueryHandler;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.response.MessageResponse;
import iuh.fit.chatservice.presentation.mapper.ChatPresentationMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/conversations/{conversationId}")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Message Pin Management", description = "APIs for pinning and unpinning messages in chat conversations")
@SecurityRequirement(name = "bearerAuth")
public class MessagePinController {

    PinMessageCommandHandler pinMessageCommandHandler;
    UnpinMessageCommandHandler unpinMessageCommandHandler;
    GetPinnedMessagesQueryHandler getPinnedMessagesQueryHandler;
    SimpMessagingTemplate messagingTemplate;
    ChatPresentationMapper chatPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/messages/{messageId}/pin")
    @Operation(summary = "Pin message in conversation", description = "Pins a specific message in the chat conversation for all members")
    public ResponseEntity<ApiResponse<MessageResponse>> pinMessage(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        UUID currentUserId = getCurrentUserId();
        PinMessageCommand command = PinMessageCommand.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .userId(currentUserId)
                .build();

        Message pinnedMessage = pinMessageCommandHandler.handle(command);
        GetMessagesResult result = GetMessagesResult.builder()
                .messageId(pinnedMessage.getId())
                .conversationId(pinnedMessage.getConversationId())
                .senderId(pinnedMessage.getSenderId())
                .type(pinnedMessage.getType())
                .content(pinnedMessage.getContent())
                .mediaUrl(pinnedMessage.getMediaUrl())
                .replyToMessageId(pinnedMessage.getReplyToMessageId())
                .edited(pinnedMessage.isEdited())
                .deleted(pinnedMessage.isDeleted())
                .pinned(pinnedMessage.isPinned())
                .pinnedAt(pinnedMessage.getPinnedAt())
                .pinnedById(pinnedMessage.getPinnedById())
                .build();

        MessageResponse response = chatPresentationMapper.toResponse(result);

        // Real-time broadcast
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/pin", response);

        return ResponseEntity.ok(ApiResponse.success(response, "Message pinned successfully"));
    }

    @DeleteMapping("/messages/{messageId}/pin")
    @Operation(summary = "Unpin message from conversation", description = "Unpins a pinned message in the chat conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> unpinMessage(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        UUID currentUserId = getCurrentUserId();
        UnpinMessageCommand command = UnpinMessageCommand.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .userId(currentUserId)
                .build();

        Message unpinnedMessage = unpinMessageCommandHandler.handle(command);
        GetMessagesResult result = GetMessagesResult.builder()
                .messageId(unpinnedMessage.getId())
                .conversationId(unpinnedMessage.getConversationId())
                .senderId(unpinnedMessage.getSenderId())
                .type(unpinnedMessage.getType())
                .content(unpinnedMessage.getContent())
                .mediaUrl(unpinnedMessage.getMediaUrl())
                .replyToMessageId(unpinnedMessage.getReplyToMessageId())
                .edited(unpinnedMessage.isEdited())
                .deleted(unpinnedMessage.isDeleted())
                .pinned(false)
                .build();

        MessageResponse response = chatPresentationMapper.toResponse(result);

        // Real-time broadcast
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/unpin", response);

        return ResponseEntity.ok(ApiResponse.success(response, "Message unpinned successfully"));
    }

    @GetMapping("/pinned-messages")
    @Operation(summary = "Get pinned messages list", description = "Retrieves all active pinned messages in a conversation ordered by pinned date")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getPinnedMessages(@PathVariable UUID conversationId) {
        UUID currentUserId = getCurrentUserId();
        GetPinnedMessagesQuery query = GetPinnedMessagesQuery.builder()
                .conversationId(conversationId)
                .userId(currentUserId)
                .build();

        List<GetMessagesResult> results = getPinnedMessagesQueryHandler.handle(query);
        List<MessageResponse> responseList = chatPresentationMapper.toMessageResponseList(results);

        return ResponseEntity.ok(ApiResponse.success(responseList, "Pinned messages retrieved successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
