package iuh.fit.chatservice.application.features.message.commands.unpin_message;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnpinMessageCommandHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;

    @Transactional
    public Message handle(UnpinMessageCommand command) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversationId().equals(command.getConversationId())) {
            throw new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND);
        }

        message.setPinned(false);
        message.setPinnedAt(null);
        message.setPinnedById(null);

        return messageRepository.save(message);
    }
}
