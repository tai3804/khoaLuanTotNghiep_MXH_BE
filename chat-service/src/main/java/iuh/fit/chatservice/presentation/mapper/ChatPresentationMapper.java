package iuh.fit.chatservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.chatservice.application.features.conversation.commands.create_direct_chat.CreateDirectChatCommand;
import iuh.fit.chatservice.application.features.conversation.commands.create_group_chat.CreateGroupChatCommand;
import iuh.fit.chatservice.application.features.conversation.commands.update_group_info.UpdateGroupInfoCommand;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailResult;
import iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations.GetUserConversationsResult;
import iuh.fit.chatservice.application.features.member.commands.add_members.AddGroupMembersCommand;
import iuh.fit.chatservice.application.features.member.commands.demote_admin.DemoteAdminCommand;
import iuh.fit.chatservice.application.features.member.commands.leave_group.LeaveGroupCommand;
import iuh.fit.chatservice.application.features.member.commands.promote_admin.PromoteAdminCommand;
import iuh.fit.chatservice.application.features.member.commands.remove_member.RemoveGroupMemberCommand;
import iuh.fit.chatservice.application.features.member.commands.update_nickname.UpdateNicknameCommand;
import iuh.fit.chatservice.application.features.message.commands.delete_message.DeleteMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.mark_as_read.MarkAsReadCommand;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageCommand;
import iuh.fit.chatservice.application.features.message.commands.send_message.SendMessageResult;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import iuh.fit.chatservice.presentation.dto.request.*;
import iuh.fit.chatservice.presentation.dto.response.ConversationDetailResponse;
import iuh.fit.chatservice.presentation.dto.response.ConversationResponse;
import iuh.fit.chatservice.presentation.dto.response.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatPresentationMapper {

    CreateDirectChatCommand toCreateDirectCommand(CreateDirectChatRequest request, UUID currentUserId);

    CreateGroupChatCommand toCreateGroupCommand(CreateGroupChatRequest request, UUID currentUserId);

    UpdateGroupInfoCommand toUpdateGroupInfoCommand(UpdateGroupInfoRequest request, UUID conversationId, UUID currentUserId);

    AddGroupMembersCommand toAddMembersCommand(AddGroupMembersRequest request, UUID conversationId, UUID currentUserId);

    RemoveGroupMemberCommand toRemoveMemberCommand(UUID conversationId, UUID targetUserId, UUID currentUserId);

    PromoteAdminCommand toPromoteAdminCommand(UUID conversationId, UUID targetUserId, UUID currentUserId);

    DemoteAdminCommand toDemoteAdminCommand(UUID conversationId, UUID targetUserId, UUID currentUserId);

    LeaveGroupCommand toLeaveGroupCommand(UUID conversationId, UUID currentUserId);

    UpdateNicknameCommand toUpdateNicknameCommand(UpdateNicknameRequest request, UUID conversationId, UUID targetUserId, UUID currentUserId);

    SendMessageCommand toSendMessageCommand(SendMessageRequest request, UUID conversationId, UUID senderId);

    DeleteMessageCommand toDeleteMessageCommand(UUID messageId, UUID currentUserId);

    MarkAsReadCommand toMarkAsReadCommand(UUID conversationId, UUID messageId, UUID currentUserId);

    ConversationResponse toResponse(GetUserConversationsResult result);

    List<ConversationResponse> toConversationResponseList(List<GetUserConversationsResult> results);

    PagedResponse<ConversationResponse> toPagedConversationResponse(PagedResponse<GetUserConversationsResult> pagedResult);

    ConversationDetailResponse toResponse(GetConversationDetailResult result);

    MessageResponse toResponse(SendMessageResult result);

    MessageResponse toResponse(GetMessagesResult result);

    List<MessageResponse> toMessageResponseList(List<GetMessagesResult> results);

    PagedResponse<MessageResponse> toPagedMessageResponse(PagedResponse<GetMessagesResult> pagedResult);
}
