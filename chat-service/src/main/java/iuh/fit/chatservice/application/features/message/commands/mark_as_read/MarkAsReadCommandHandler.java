package iuh.fit.chatservice.application.features.message.commands.mark_as_read;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarkAsReadCommandHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;

    @Transactional
    public void handle(MarkAsReadCommand command) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        UUID targetMessageId = command.getMessageId();
        if (targetMessageId == null) {
            Optional<Message> latestOpt = messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDesc(command.getConversationId());
            if (latestOpt.isPresent()) {
                targetMessageId = latestOpt.get().getId();
            }
        }

        if (targetMessageId != null) {
            member.setLastReadMessageId(targetMessageId);
            conversationMemberRepository.save(member);
        }
    }
}
