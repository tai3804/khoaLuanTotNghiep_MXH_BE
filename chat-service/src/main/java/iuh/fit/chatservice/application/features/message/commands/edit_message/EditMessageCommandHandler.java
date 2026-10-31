package iuh.fit.chatservice.application.features.message.commands.edit_message;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
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
public class EditMessageCommandHandler {

    MessageRepository messageRepository;
    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public Message handle(EditMessageCommand command) {
        boolean isMember = conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversationId().equals(command.getConversationId()) || message.isDeleted()) {
            throw new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND);
        }

        if (message.getSenderId() == null || !message.getSenderId().equals(command.getCurrentUserId())) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED_MESSAGE_DELETE);
        }

        message.setContent(command.getNewContent());
        message.setEdited(true);
        Message saved = messageRepository.save(message);

        // If this message is the latest active message in the conversation, update conversation's last message
        conversationRepository.findById(command.getConversationId()).ifPresent(conversation -> {
            messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDesc(command.getConversationId())
                    .ifPresent(latest -> {
                        if (latest.getId().equals(saved.getId()) && saved.getContent() != null) {
                            conversation.setLastMessageContent(saved.getContent());
                            conversationRepository.save(conversation);
                        }
                    });
        });

        log.info("Message {} edited successfully by user {}", saved.getId(), command.getCurrentUserId());
        return saved;
    }
}
