package iuh.fit.chatservice.application.features.message.queries.get_messages;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.chatservice.application.exception.ChatServiceErrorCode;
import iuh.fit.chatservice.application.mapper.MessageFeatureMapper;
import iuh.fit.chatservice.domain.entities.Message;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import iuh.fit.chatservice.infrastructure.persistence.repository.ConversationMemberRepository;
import iuh.fit.chatservice.infrastructure.persistence.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetMessagesQueryHandler {

    ConversationMemberRepository conversationMemberRepository;
    MessageRepository messageRepository;
    MessageFeatureMapper messageFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetMessagesResult> handle(GetMessagesQuery query) {
        boolean isMember = conversationMemberRepository
                .existsByConversationIdAndUserIdAndStatus(query.getConversationId(), query.getCurrentUserId(), MemberStatus.ACTIVE);
        if (!isMember) {
            throw new BusinessException(ChatServiceErrorCode.NOT_A_CONVERSATION_MEMBER);
        }

        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize());
        Page<Message> messagePage = messageRepository.findByConversationIdAndDeletedFalseOrderByCreatedAtDesc(
                query.getConversationId(), pageRequest
        );

        List<GetMessagesResult> results = messagePage.getContent().stream()
                .map(messageFeatureMapper::toGetMessagesResult)
                .toList();

        return PagedResponse.<GetMessagesResult>builder()
                .content(results)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .last(messagePage.isLast())
                .build();
    }
}
