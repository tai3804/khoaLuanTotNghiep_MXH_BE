package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.message.commands.delete_message.DeleteMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.delete_message.DeleteMessageCommandHandler;
import iuh.fit.chatservice.application.features.message.commands.mark_as_read.MarkAsReadCommand;
import iuh.fit.chatservice.application.features.message.commands.mark_as_read.MarkAsReadCommandHandler;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageCommandHandler;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageResult;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesQuery;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesQueryHandler;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.request.SendMessageRequest;
import iuh.fit.chatservice.presentation.dto.response.MessageResponse;
import iuh.fit.chatservice.presentation.mapper.ChatPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/conversations/{conversationId}/messages")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Message Management", description = "APIs for sending, retrieving, deleting, and marking messages as read")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    SendMessageCommandHandler sendMessageCommandHandler;
    DeleteMessageCommandHandler deleteMessageCommandHandler;
    MarkAsReadCommandHandler markAsReadCommandHandler;
    GetMessagesQueryHandler getMessagesQueryHandler;
    SimpMessagingTemplate messagingTemplate;
    ChatPresentationMapper chatPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Send message via REST API", description = "Sends a message in a conversation via REST API (also broadcasts to WebSocket subscribers)")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        UUID currentUserId = getCurrentUserId();
        SendMessageCommand command = chatPresentationMapper.toSendMessageCommand(request, conversationId, currentUserId);
        SendMessageResult result = sendMessageCommandHandler.handle(command);
        MessageResponse response = chatPresentationMapper.toResponse(result);

        // Real-time broadcast to conversation topic
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);

        return ResponseEntity.ok(ApiResponse.success(response, "Message sent successfully"));
    }

    @GetMapping
    @Operation(summary = "Get conversation messages", description = "Retrieves paginated message history of a conversation in reverse chronological order")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = getCurrentUserId();
        GetMessagesQuery query = GetMessagesQuery.builder()
                .conversationId(conversationId)
                .currentUserId(currentUserId)
                .page(page)
                .size(size)
                .build();
        PagedResponse<GetMessagesResult> result = getMessagesQueryHandler.handle(query);
        PagedResponse<MessageResponse> pagedResponse = chatPresentationMapper.toPagedMessageResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Messages retrieved successfully"));
    }

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete message", description = "Soft-deletes a message (Only the sender can delete their message)")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        UUID currentUserId = getCurrentUserId();
        DeleteMessageCommand command = chatPresentationMapper.toDeleteMessageCommand(messageId, currentUserId);
        deleteMessageCommandHandler.handle(command);

        // Broadcast deletion event to WebSocket
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/deleted", messageId);

        return ResponseEntity.ok(ApiResponse.success(null, "Message deleted successfully"));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark messages as read", description = "Updates last read message marker for current user in conversation")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID messageId) {
        UUID currentUserId = getCurrentUserId();
        MarkAsReadCommand command = chatPresentationMapper.toMarkAsReadCommand(conversationId, messageId, currentUserId);
        markAsReadCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Marked as read successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
