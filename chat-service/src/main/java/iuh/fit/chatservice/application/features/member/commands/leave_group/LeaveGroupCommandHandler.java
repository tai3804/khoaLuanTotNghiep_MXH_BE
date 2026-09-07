package iuh.fit.chatservice.application.features.member.commands.leave_group;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaveGroupCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public void handle(LeaveGroupCommand command) {
        Conversation conversation = conversationRepository.findById(command.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException(ChatServiceErrorCode.INVALID_CONVERSATION_TYPE);
        }

        ConversationMember currentMember = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        currentMember.setStatus(MemberStatus.LEFT);
        conversationMemberRepository.save(currentMember);

        // If the leaving user was an ADMIN, check if remaining active members need an ADMIN
        if (currentMember.getRole() == MemberRole.ADMIN) {
            List<ConversationMember> activeMembers = conversationMemberRepository
                    .findActiveMembersOrderedByJoinedAt(command.getConversationId());

            if (!activeMembers.isEmpty()) {
                boolean hasOtherAdmin = activeMembers.stream().anyMatch(m -> m.getRole() == MemberRole.ADMIN);
                if (!hasOtherAdmin) {
                    // Auto-promote the oldest active member to ADMIN
                    ConversationMember oldestMember = activeMembers.get(0);
                    oldestMember.setRole(MemberRole.ADMIN);
                    conversationMemberRepository.save(oldestMember);
                }
            }
        }
    }
}
