package iuh.fit.chatservice.application.features.message.commands.send_message;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.mapper.MessageFeatureMapper;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.domain.enums.MessageType;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SendMessageCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageFeatureMapper messageFeatureMapper;

    @Transactional
    public SendMessageResult handle(SendMessageCommand command) {
        Conversation conversation = conversationRepository.findById(command.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        if (command.getSenderId() != null) {
            ConversationMember member = conversationMemberRepository
                    .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getSenderId(), MemberStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

            // Auto-update lastReadMessageId for sender
            // Note: message ID will be generated upon save
        }

        MessageType type = command.getType() != null ? command.getType() : MessageType.TEXT;

        Message message = Message.builder()
                .conversationId(command.getConversationId())
                .senderId(command.getSenderId())
                .type(type)
                .content(command.getContent())
                .mediaUrl(command.getMediaUrl())
                .replyToMessageId(command.getReplyToMessageId())
                .build();

        message = messageRepository.save(message);

        // Update last message in conversation
        conversation.setLastMessageContent(command.getContent() != null ? command.getContent() : "[" + type.name() + "]");
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Update lastReadMessageId for sender
        if (command.getSenderId() != null) {
            final Message savedMessage = message;
            conversationMemberRepository.findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getSenderId(), MemberStatus.ACTIVE)
                    .ifPresent(m -> {
                        m.setLastReadMessageId(savedMessage.getId());
                        conversationMemberRepository.save(m);
                    });
        }

        return messageFeatureMapper.toSendMessageResult(message);
    }
}
