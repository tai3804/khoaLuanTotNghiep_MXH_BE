package iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.chatservice.application.mapper.ConversationFeatureMapper;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
import iuh.fit.chatservice.domain.enums.ConversationType;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserConversationsQueryHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    ConversationFeatureMapper conversationFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetUserConversationsResult> handle(GetUserConversationsQuery query) {
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize());
        Page<ConversationMember> memberPage = conversationMemberRepository.findByUserIdAndStatus(
                query.getCurrentUserId(), MemberStatus.ACTIVE, pageRequest
        );

        List<GetUserConversationsResult> contentList = new ArrayList<>();
        for (ConversationMember member : memberPage.getContent()) {
            Optional<Conversation> convOpt = conversationRepository.findById(member.getConversationId());
            if (convOpt.isEmpty() || convOpt.get().isDeleted()) {
                continue;
            }

            Conversation conversation = convOpt.get();
            GetUserConversationsResult result = conversationFeatureMapper.toUserConversationsResult(conversation);

            // Calculate unread count for current user
            long unread = messageRepository.countUnreadMessages(conversation.getId(), member.getLastReadMessageId());
            result.setUnreadCount(unread);

            // If DIRECT, find the other participant ID
            if (conversation.getType() == ConversationType.DIRECT) {
                List<ConversationMember> members = conversationMemberRepository.findByConversationIdAndStatus(
                        conversation.getId(), MemberStatus.ACTIVE
                );
                members.stream()
                        .filter(m -> !m.getUserId().equals(query.getCurrentUserId()))
                        .findFirst()
                        .ifPresent(other -> result.setOtherParticipantId(other.getUserId()));
            }

            contentList.add(result);
        }

        return PagedResponse.<GetUserConversationsResult>builder()
                .content(contentList)
                .page(memberPage.getNumber())
                .size(memberPage.getSize())
                .totalElements(memberPage.getTotalElements())
                .totalPages(memberPage.getTotalPages())
                .last(memberPage.isLast())
                .build();
    }
}
