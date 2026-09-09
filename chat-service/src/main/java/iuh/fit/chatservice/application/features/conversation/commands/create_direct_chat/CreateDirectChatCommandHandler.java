package iuh.fit.chatservice.application.features.conversation.commands.create_direct_chat;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail.GetConversationDetailResult;
import iuh.fit.chatservice.application.mapper.ConversationFeatureMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateDirectChatCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    ConversationFeatureMapper conversationFeatureMapper;

    @Transactional
    public GetConversationDetailResult handle(CreateDirectChatCommand command) {
        if (command.getCurrentUserId().equals(command.getTargetUserId())) {
            throw new BusinessException(ChatServiceErrorCode.CANNOT_CHAT_WITH_SELF);
        }

        // Check if direct conversation already exists
        List<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(
                ConversationType.DIRECT,
                command.getCurrentUserId(),
                command.getTargetUserId()
        );

        Conversation conversation;
        if (!existing.isEmpty()) {
            conversation = existing.get(0);
        } else {
            // Create new DIRECT conversation
            conversation = Conversation.builder()
                    .type(ConversationType.DIRECT)
                    .creatorId(command.getCurrentUserId())
                    .maxMembers(2)
                    .build();
            conversation = conversationRepository.save(conversation);

            ConversationMember member1 = ConversationMember.builder()
                    .conversationId(conversation.getId())
                    .userId(command.getCurrentUserId())
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();

            ConversationMember member2 = ConversationMember.builder()
                    .conversationId(conversation.getId())
                    .userId(command.getTargetUserId())
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();

            conversationMemberRepository.saveAll(List.of(member1, member2));
        }

        List<ConversationMember> members = conversationMemberRepository.findByConversationIdAndStatus(
                conversation.getId(), MemberStatus.ACTIVE
        );
        List<GetConversationDetailResult.MemberResult> memberResults = members.stream()
                .map(conversationFeatureMapper::toMemberResult)
                .toList();

        return conversationFeatureMapper.toConversationDetailResult(conversation, memberResults);
    }
}
