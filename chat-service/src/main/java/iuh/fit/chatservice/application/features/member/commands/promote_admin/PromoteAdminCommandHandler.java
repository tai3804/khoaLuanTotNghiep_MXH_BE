package iuh.fit.chatservice.application.features.member.commands.promote_admin;

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
public class PromoteAdminCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public void handle(PromoteAdminCommand command) {
        Conversation conversation = conversationRepository.findById(command.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException(ChatServiceErrorCode.INVALID_CONVERSATION_TYPE);
        }

        ConversationMember currentMember = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        if (currentMember.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_GROUP_ADMIN);
        }

        ConversationMember targetMember = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getTargetUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        targetMember.setRole(MemberRole.ADMIN);
        conversationMemberRepository.save(targetMember);
    }
}
