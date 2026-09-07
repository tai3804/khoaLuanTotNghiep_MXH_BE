package iuh.fit.chatservice.application.features.message.commands.delete_message;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeleteMessageCommandHandler {

    MessageRepository messageRepository;

    @Transactional
    public void handle(DeleteMessageCommand command) {
        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (message.getSenderId() == null || !message.getSenderId().equals(command.getCurrentUserId())) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED_MESSAGE_DELETE);
        }

        message.setDeleted(true);
        messageRepository.save(message);
    }
}
