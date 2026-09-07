package iuh.fit.chatservice.application.features.conversation.commands.create_group_chat;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateGroupChatCommandHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    ConversationFeatureMapper conversationFeatureMapper;

    @Transactional
    public GetConversationDetailResult handle(CreateGroupChatCommand command) {
        Set<UUID> allMemberIds = new HashSet<>();
        if (command.getMemberIds() != null) {
            allMemberIds.addAll(command.getMemberIds());
        }
        allMemberIds.add(command.getCurrentUserId());

        if (allMemberIds.size() > 250) {
            throw new BusinessException(ChatServiceErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
        }

        String groupName = (command.getName() != null && !command.getName().isBlank())
                ? command.getName().trim()
                : "Group Chat";

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(groupName)
                .avatarUrl(command.getAvatarUrl())
                .creatorId(command.getCurrentUserId())
                .maxMembers(250)
                .build();
        conversation = conversationRepository.save(conversation);

        List<ConversationMember> membersToSave = new ArrayList<>();
        for (UUID userId : allMemberIds) {
            MemberRole role = userId.equals(command.getCurrentUserId()) ? MemberRole.ADMIN : MemberRole.MEMBER;
            ConversationMember member = ConversationMember.builder()
                    .conversationId(conversation.getId())
                    .userId(userId)
                    .role(role)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();
            membersToSave.add(member);
        }

        conversationMemberRepository.saveAll(membersToSave);

        List<GetConversationDetailResult.MemberResult> memberResults = membersToSave.stream()
                .map(conversationFeatureMapper::toMemberResult)
                .toList();

        return conversationFeatureMapper.toConversationDetailResult(conversation, memberResults);
    }
}
