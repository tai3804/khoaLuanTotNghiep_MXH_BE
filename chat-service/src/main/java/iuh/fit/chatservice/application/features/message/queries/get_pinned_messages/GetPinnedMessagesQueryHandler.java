package iuh.fit.chatservice.application.features.message.queries.get_pinned_messages;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.message.queries.get_messages.GetMessagesResult;
import iuh.fit.chatservice.application.mapper.MessageFeatureMapper;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPinnedMessagesQueryHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageFeatureMapper messageFeatureMapper;

    @Transactional(readOnly = true)
    public List<GetMessagesResult> handle(GetPinnedMessagesQuery query) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(query.getConversationId(), query.getUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        List<Message> pinnedMessages = messageRepository
                .findByConversationIdAndPinnedTrueAndDeletedFalseOrderByPinnedAtDesc(query.getConversationId());

        return pinnedMessages.stream()
                .map(messageFeatureMapper::toGetMessagesResult)
                .toList();
    }
}
