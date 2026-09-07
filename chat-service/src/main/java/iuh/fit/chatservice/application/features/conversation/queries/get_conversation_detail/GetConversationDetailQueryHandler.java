package iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.mapper.ConversationFeatureMapper;
import iuh.fit.chatservice.domain.entities.Conversation;
import iuh.fit.chatservice.domain.entities.ConversationMember;
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
public class GetConversationDetailQueryHandler {

    ConversationRepository conversationRepository;
    ConversationMemberRepository conversationMemberRepository;
    ConversationFeatureMapper conversationFeatureMapper;

    @Transactional(readOnly = true)
    public GetConversationDetailResult handle(GetConversationDetailQuery query) {
        Conversation conversation = conversationRepository.findById(query.getConversationId())
                .orElseThrow(() -> new BusinessException(ChatServiceErrorCode.CONVERSATION_NOT_FOUND));

        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(query.getConversationId(), query.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        List<ConversationMember> members = conversationMemberRepository.findByConversationIdAndStatus(
                query.getConversationId(), MemberStatus.ACTIVE
        );
        List<GetConversationDetailResult.MemberResult> memberResults = members.stream()
                .map(conversationFeatureMapper::toMemberResult)
                .toList();

        return conversationFeatureMapper.toConversationDetailResult(conversation, memberResults);
    }
}
