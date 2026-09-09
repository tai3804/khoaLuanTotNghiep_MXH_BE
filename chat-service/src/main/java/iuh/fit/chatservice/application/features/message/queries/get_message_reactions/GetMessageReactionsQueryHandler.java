package iuh.fit.chatservice.application.features.message.queries.get_message_reactions;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.MessageReaction;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetMessageReactionsQueryHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageReactionRepository messageReactionRepository;

    @Transactional(readOnly = true)
    public List<MessageReaction> handle(GetMessageReactionsQuery query) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(query.getConversationId(), query.getUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        return messageReactionRepository.findByMessageId(query.getMessageId());
    }
}
