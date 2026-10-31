package iuh.fit.chatservice.application.features.message.commands.recall_message;

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

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecallMessageHandler {

    MessageRepository messageRepository;
    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public Message handle(RecallMessageCommand command) {
        boolean isMember = conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversationId().equals(command.getConversationId())) {
            throw new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND);
        }

        if (message.getSenderId() == null || !message.getSenderId().equals(command.getCurrentUserId())) {
            throw new BusinessException(ChatServiceErrorCode.UNAUTHORIZED_MESSAGE_DELETE);
        }

        message.setDeleted(true);
        message.setContent("");
        message.setMediaUrl(null);
        if (message.isPinned()) {
            message.setPinned(false);
            message.setPinnedAt(null);
            message.setPinnedById(null);
        }
        Message saved = messageRepository.save(message);

        conversationRepository.findById(command.getConversationId()).ifPresent(conversation -> {
            Optional<Message> latestActiveOpt = messageRepository
                    .findTopByConversationIdAndDeletedFalseOrderByCreatedAtDesc(command.getConversationId());
            if (latestActiveOpt.isPresent()) {
                Message latest = latestActiveOpt.get();
                conversation.setLastMessageContent(
                        latest.getContent() != null && !latest.getContent().isBlank()
                                ? latest.getContent()
                                : "[" + latest.getType().name() + "]"
                );
                conversation.setLastMessageAt(latest.getCreatedAt());
                conversation.setLastMessageSenderId(latest.getSenderId());
            } else {
                conversation.setLastMessageContent("Tin nhắn đã được thu hồi");
            }
            conversationRepository.save(conversation);
        });

        log.info("Recalled message {} for everyone in conversation {}", saved.getId(), command.getConversationId());
        return saved;
    }
}
