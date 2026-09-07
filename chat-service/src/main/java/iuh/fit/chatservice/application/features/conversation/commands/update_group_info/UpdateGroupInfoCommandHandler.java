package iuh.fit.chatservice.application.features.conversation.commands.update_group_info;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.enums.ConversationType;
import iuh.fit.chatservice.domain.enums.MemberRole;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdateGroupInfoCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public void handle(UpdateGroupInfoCommand command) {
        Conversation conversation = conversationRepository.findById(command.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException(ChatServiceErrorCode.INVALID_CONVERSATION_TYPE);
        }

        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        if (member.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_GROUP_ADMIN);
        }

        if (command.getName() != null && !command.getName().isBlank()) {
            conversation.setName(command.getName().trim());
        }

        if (command.getAvatarUrl() != null) {
            conversation.setAvatarUrl(command.getAvatarUrl());
        }

        conversationRepository.save(conversation);
    }
}
