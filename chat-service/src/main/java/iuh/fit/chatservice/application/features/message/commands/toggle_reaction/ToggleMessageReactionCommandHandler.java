package iuh.fit.chatservice.application.features.message.commands.toggle_reaction;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.entities.MessageReaction;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageReactionRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToggleMessageReactionCommandHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageReactionRepository messageReactionRepository;

    @Transactional
    public Optional<MessageReaction> handle(ToggleMessageReactionCommand command) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        Message message = messageRepository.findById(command.getMessageId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversationId().equals(command.getConversationId()) || message.isDeleted()) {
            throw new BusinessException(ChatServiceErrorCode.MESSAGE_NOT_FOUND);
        }

        Optional<MessageReaction> existingOpt = messageReactionRepository.findByMessageIdAndUserId(command.getMessageId(), command.getUserId());

        if (existingOpt.isPresent()) {
            MessageReaction reaction = existingOpt.get();
            if (reaction.getEmoji().equals(command.getEmoji())) {
                // Remove reaction if same emoji toggled
                messageReactionRepository.delete(reaction);
                return Optional.empty();
            } else {
                // Update to new emoji
                reaction.setEmoji(command.getEmoji());
                return Optional.of(messageReactionRepository.save(reaction));
            }
        } else {
            // Add new reaction
            MessageReaction reaction = MessageReaction.builder()
                    .messageId(command.getMessageId())
                    .conversationId(command.getConversationId())
                    .userId(command.getUserId())
                    .emoji(command.getEmoji())
                    .build();
            return Optional.of(messageReactionRepository.save(reaction));
        }
    }
}
