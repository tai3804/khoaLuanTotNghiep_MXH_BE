package iuh.fit.chatservice.application.features.message.commands.delete_for_me;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.entities.MessageUserDeletion;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageUserDeletionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteMessageForMeHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageUserDeletionRepository messageUserDeletionRepository;

    @Transactional
    public void handle(DeleteMessageForMeCommand command) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (!messageUserDeletionRepository.existsByMessageIdAndUserId(message.getId(), command.getCurrentUserId())) {
            MessageUserDeletion deletion = MessageUserDeletion.builder()
                    .messageId(message.getId())
                    .userId(command.getCurrentUserId())
                    .build();
            messageUserDeletionRepository.save(deletion);
            log.info("Deleted message {} for user {}", message.getId(), command.getCurrentUserId());
        }
    }
}
