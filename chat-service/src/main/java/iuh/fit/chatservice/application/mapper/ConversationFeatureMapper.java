package iuh.fit.chatservice.application.mapper;

import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations.GetUserConversationsResult;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConversationFeatureMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "type", source = "conversation.type")
    @Mapping(target = "name", source = "conversation.name")
    @Mapping(target = "avatarUrl", source = "conversation.avatarUrl")
    @Mapping(target = "lastMessageContent", source = "conversation.lastMessageContent")
    @Mapping(target = "lastMessageAt", source = "conversation.lastMessageAt")
    GetUserConversationsResult toUserConversationsResult(Conversation conversation);

    @Mapping(target = "conversationId", source = "conversation.id")
    GetConversationDetailResult toConversationDetailResult(Conversation conversation, List<GetConversationDetailResult.MemberResult> members);

    GetConversationDetailResult.MemberResult toMemberResult(ConversationMember member);
}
