package iuh.fit.chatservice.application.features.message.commands.edit_message;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
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

    @Transactional
    public Message handle(EditMessageCommand command) {
        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (message.getSenderId() == null || !message.getSenderId().equals(command.getCurrentUserId())) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED_MESSAGE_DELETE);
        }

        if (message.isDeleted()) {
            throw new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND);
        }

        message.setContent(command.getNewContent());
        message.setEdited(true);
        Message saved = messageRepository.save(message);

        // If this message was the latest message in the conversation, update conversation's last message
        conversationRepository.findById(command.getConversationId()).ifPresent(conversation -> {
            if (saved.getContent() != null) {
                if (conversation.getLastMessageAt() == null || !saved.getCreatedAt().isBefore(conversation.getLastMessageAt())) {
                    conversation.setLastMessageContent(saved.getContent());
                    conversationRepository.save(conversation);
                }
            }
        });

        log.info("Message {} edited successfully by user {}", saved.getId(), command.getCurrentUserId());
        return saved;
    }
}
