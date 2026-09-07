package iuh.fit.chatservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.conversation.commands.create_direct_chat.CreateDirectChatCommand;
import iuh.fit.chatservice.application.features.conversation.commands.create_direct_chat.CreateDirectChatCommandHandler;
import iuh.fit.chatservice.application.features.conversation.commands.create_group_chat.CreateGroupChatCommand;
import iuh.fit.chatservice.application.features.conversation.commands.create_group_chat.CreateGroupChatCommandHandler;
import iuh.fit.chatservice.application.features.conversation.commands.update_group_info.UpdateGroupInfoCommand;
import iuh.fit.chatservice.application.features.conversation.commands.update_group_info.UpdateGroupInfoCommandHandler;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailQuery;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailQueryHandler;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailResult;
import iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations.GetUserConversationsQuery;
import iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations.GetUserConversationsQueryHandler;
import iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations.GetUserConversationsResult;
import iuh.fit.chatservice.presentation.constants.ApiConstants;
import iuh.fit.chatservice.presentation.dto.request.CreateDirectChatRequest;
import iuh.fit.chatservice.presentation.dto.request.CreateGroupChatRequest;
import iuh.fit.chatservice.presentation.dto.request.UpdateGroupInfoRequest;
import iuh.fit.chatservice.presentation.dto.response.ConversationDetailResponse;
import iuh.fit.chatservice.presentation.dto.response.ConversationResponse;
import iuh.fit.chatservice.presentation.mapper.ChatPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CHAT_API + "/conversations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Conversation Management", description = "APIs for direct chat, group chat creation, and conversation listing")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    CreateDirectChatCommandHandler createDirectChatCommandHandler;
    CreateGroupChatCommandHandler createGroupChatCommandHandler;
    UpdateGroupInfoCommandHandler updateGroupInfoCommandHandler;
    GetUserConversationsQueryHandler getUserConversationsQueryHandler;
    GetConversationDetailQueryHandler getConversationDetailQueryHandler;
    ChatPresentationMapper chatPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/direct")
    @Operation(summary = "Get or create 1-on-1 direct conversation", description = "Retrieves existing or creates a new direct 1-on-1 conversation between two users")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> createDirectChat(@Valid @RequestBody CreateDirectChatRequest request) {
        UUID currentUserId = getCurrentUserId();
        CreateDirectChatCommand command = chatPresentationMapper.toCreateDirectCommand(request, currentUserId);
        GetConversationDetailResult result = createDirectChatCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(chatPresentationMapper.toResponse(result), "Direct conversation retrieved successfully"));
    }

    @PostMapping("/group")
    @Operation(summary = "Create a new group chat", description = "Creates a new group conversation with initial members (up to 250 members)")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> createGroupChat(@Valid @RequestBody CreateGroupChatRequest request) {
        UUID currentUserId = getCurrentUserId();
        CreateGroupChatCommand command = chatPresentationMapper.toCreateGroupCommand(request, currentUserId);
        GetConversationDetailResult result = createGroupChatCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(chatPresentationMapper.toResponse(result), "Group chat created successfully"));
    }

    @GetMapping
    @Operation(summary = "Get current user conversations", description = "Retrieves paginated list of active conversations for the authenticated user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = getCurrentUserId();
        GetUserConversationsQuery query = GetUserConversationsQuery.builder()
                .currentUserId(currentUserId)
                .page(page)
                .size(size)
                .build();
        PagedResponse<GetUserConversationsResult> result = getUserConversationsQueryHandler.handle(query);
        PagedResponse<ConversationResponse> pagedResponse = chatPresentationMapper.toPagedConversationResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Conversations retrieved successfully"));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation details", description = "Retrieves details and member list of a specific conversation")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversationDetail(@PathVariable UUID conversationId) {
        UUID currentUserId = getCurrentUserId();
        GetConversationDetailQuery query = GetConversationDetailQuery.builder()
                .conversationId(conversationId)
                .currentUserId(currentUserId)
                .build();
        GetConversationDetailResult result = getConversationDetailQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.success(chatPresentationMapper.toResponse(result), "Conversation detail retrieved successfully"));
    }

    @PutMapping("/{conversationId}/group-info")
    @Operation(summary = "Update group info", description = "Updates group name or group avatar (Admin only)")
    public ResponseEntity<ApiResponse<Void>> updateGroupInfo(
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateGroupInfoRequest request) {
        UUID currentUserId = getCurrentUserId();
        UpdateGroupInfoCommand command = chatPresentationMapper.toUpdateGroupInfoCommand(request, conversationId, currentUserId);
        updateGroupInfoCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Group info updated successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
