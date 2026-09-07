package iuh.fit.chatservice.application.features.member.commands.add_members;

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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddGroupMembersCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;

    @Transactional
    public void handle(AddGroupMembersCommand command) {
        Conversation conversation = conversationRepository.findById(command.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException(ChatServiceErrorCode.INVALID_CONVERSATION_TYPE);
        }

        ConversationMember currentMember = conversationMemberRepository
                .findByConversationIdAndUserIdAndStatus(command.getConversationId(), command.getCurrentUserId(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER));

        long activeCount = conversationMemberRepository.countByConversationIdAndStatus(command.getConversationId(), MemberStatus.ACTIVE);
        int newUsersCount = command.getUserIdsToAdd() != null ? command.getUserIdsToAdd().size() : 0;

        if (activeCount + newUsersCount > conversation.getMaxMembers()) {
            throw new BusinessException(ChatServiceErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
        }

        if (command.getUserIdsToAdd() != null) {
            for (UUID targetUserId : command.getUserIdsToAdd()) {
                Optional<ConversationMember> existingOpt = conversationMemberRepository
                        .findByConversationIdAndUserId(command.getConversationId(), targetUserId);

                if (existingOpt.isPresent()) {
                    ConversationMember existingMember = existingOpt.get();
                    if (existingMember.getStatus() == MemberStatus.ACTIVE) {
                        continue; // Already active in group
                    }
                    existingMember.setStatus(MemberStatus.ACTIVE);
                    existingMember.setRole(MemberRole.MEMBER);
                    existingMember.setJoinedAt(LocalDateTime.now());
                    conversationMemberRepository.save(existingMember);
                } else {
                    ConversationMember newMember = ConversationMember.builder()
                            .conversationId(command.getConversationId())
                            .userId(targetUserId)
                            .role(MemberRole.MEMBER)
                            .status(MemberStatus.ACTIVE)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    conversationMemberRepository.save(newMember);
                }
            }
        }
    }
}
